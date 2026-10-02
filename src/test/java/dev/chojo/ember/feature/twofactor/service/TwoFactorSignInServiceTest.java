/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.TwoFactorRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService.VerifyBackupCodeResult;
import dev.chojo.ember.feature.twofactor.service.TwoFactorSignInService.Attempt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A waiting sign-in is read without spending it, finished by the right code, and spent after too
 * many wrong ones.
 */
class TwoFactorSignInServiceTest {
    private static final String TOKEN = "pre-auth";
    private static final int ACCOUNT = 7;

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final TwoFactorService twoFactor = mock(TwoFactorService.class);
    private final TwoFactorAuditService audit = mock(TwoFactorAuditService.class);
    private final TwoFactorAttemptTracker attempts = mock(TwoFactorAttemptTracker.class);
    private final TwoFactorSignInService service =
            new TwoFactorSignInService(accounts, twoFactor, audit, attempts, TokenHasher.forTesting("pepper"));

    @Test
    void aWaitingSignInNamesItsAccountAndStaysUnspent() {
        waiting(TokenType.TWO_FACTOR_PENDING, Duration.ofMinutes(5));

        assertEquals(ACCOUNT, service.waitingAccount(TOKEN, TwoFactorRefusal.TWO_FACTOR_CODE_WRONG));
        verify(accounts, never()).deleteToken(TOKEN);
    }

    @Test
    void anExpiredOrForeignTokenIsRemovedAndRefused() {
        waiting(TokenType.TWO_FACTOR_PENDING, Duration.ofMinutes(-1));
        assertRefused(
                TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY,
                () -> service.waitingAccount(TOKEN, TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY));

        waiting(TokenType.RESET_PASSWORD, Duration.ofMinutes(5));
        assertRefused(
                TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY,
                () -> service.waitingAccount(TOKEN, TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY));

        verify(accounts, times(2)).deleteToken(TOKEN);
    }

    @Test
    void anUnknownTokenIsRefused() {
        when(accounts.findToken(TOKEN)).thenReturn(Optional.empty());

        assertRefused(
                TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY,
                () -> service.waitingAccount(TOKEN, TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY));
    }

    @Test
    void theRightAuthenticatorCodeSpendsTheToken() {
        when(twoFactor.verifyTotp(ACCOUNT, "123456")).thenReturn(true);

        service.verify(ACCOUNT, TOKEN, new Attempt("TOTP", "123456", "203.0.113.1", "agent", "DE"));

        verify(audit).record(ACCOUNT, null, TwoFactorEvent.LOGIN_VERIFIED, TwoFactorKind.TOTP, "agent", "DE");
        verify(attempts).reset(anyString());
        verify(accounts).deleteToken(TOKEN);
    }

    @Test
    void aBackupCodeIsCheckedAsOne() {
        when(twoFactor.verifyBackupCode(ACCOUNT, "abcd-efgh", "203.0.113.1"))
                .thenReturn(new VerifyBackupCodeResult(true, 4));

        service.verify(ACCOUNT, TOKEN, new Attempt("BACKUP_CODE", "abcd-efgh", "203.0.113.1", "agent", null));

        verify(audit).record(ACCOUNT, null, TwoFactorEvent.BACKUP_CODE_USED, TwoFactorKind.BACKUP_CODES, "agent", null);
        verify(accounts).deleteToken(TOKEN);
    }

    @Test
    void aWrongCodeIsRefusedAndTheTokenKeptUntilTooManyTries() {
        when(twoFactor.verifyTotp(anyInt(), anyString())).thenReturn(false);
        when(twoFactor.verifyBackupCode(anyInt(), anyString(), any())).thenReturn(new VerifyBackupCodeResult(false, 3));
        when(attempts.recordFailure(anyString())).thenReturn(1, TwoFactorAttemptTracker.MAX_ATTEMPTS);
        var attempt = new Attempt("TOTP", "000000", "203.0.113.1", "agent", null);

        assertRefused(TwoFactorRefusal.TWO_FACTOR_CODE_WRONG, () -> service.verify(ACCOUNT, TOKEN, attempt));
        verify(accounts, never()).deleteToken(TOKEN);

        assertRefused(TwoFactorRefusal.TWO_FACTOR_CODE_WRONG, () -> service.verify(ACCOUNT, TOKEN, attempt));
        verify(accounts).deleteToken(TOKEN);
        verify(audit, never()).record(anyInt(), any(), any(), any(), any(), any());
    }

    @Test
    void finishingSpendsTheToken() {
        service.finish(TOKEN);

        verify(accounts).deleteToken(TOKEN);
    }

    private void waiting(TokenType type, Duration left) {
        Instant now = Instant.now();
        when(accounts.findToken(TOKEN))
                .thenReturn(Optional.of(new AccountToken(1, ACCOUNT, "hash", type, null, now.plus(left), now, null)));
    }

    private static void assertRefused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }
}
