/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.TwoFactorRefusal;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * The second half of signing in: the sign-in that waits for a second factor after the password,
 * and the factor that finishes it.
 *
 * <p>What waits is a single-use pre-authentication token. It is spent once the factor checks out,
 * and after too many wrong codes, so it cannot be guessed at forever.
 */
@Singleton
public class TwoFactorSignInService {
    private final AccountRepository accounts;
    private final TwoFactorService twoFactorService;
    private final TwoFactorAuditService auditService;
    private final TwoFactorAttemptTracker attemptTracker;
    private final TokenHasher tokenHasher;

    @Inject
    public TwoFactorSignInService(
            AccountRepository accounts,
            TwoFactorService twoFactorService,
            TwoFactorAuditService auditService,
            TwoFactorAttemptTracker attemptTracker,
            TokenHasher tokenHasher) {
        this.accounts = accounts;
        this.twoFactorService = twoFactorService;
        this.auditService = auditService;
        this.attemptTracker = attemptTracker;
        this.tokenHasher = tokenHasher;
    }

    /**
     * The account a sign-in is waiting on a second factor for, without spending the token, so a
     * failed attempt can be tried again with it. A token that ran out or is not a waiting sign-in
     * is removed and refused.
     *
     * @param preAuthToken  the token the password step handed out
     * @param whenNotWaiting the refusal to answer with where nothing is waiting
     * @return the account
     */
    public int waitingAccount(String preAuthToken, Refusal whenNotWaiting) {
        var preAuth = accounts.findToken(preAuthToken).orElseThrow(whenNotWaiting::raise);
        if (preAuth.isExpired() || preAuth.tokenType() != TokenType.TWO_FACTOR_PENDING) {
            accounts.deleteToken(preAuthToken);
            throw whenNotWaiting.raise();
        }
        return preAuth.accountId();
    }

    /**
     * Spends the token once the sign-in it stood for is complete.
     */
    public void finish(String preAuthToken) {
        accounts.deleteToken(preAuthToken);
    }

    /**
     * Checks an authenticator code or a backup code for a waiting sign-in and spends the token on
     * success. A wrong code counts against the token, which is spent after too many.
     *
     * @param accountId    the account the sign-in waits for
     * @param preAuthToken the waiting sign-in
     * @param attempt      the factor and the code offered
     */
    public void verify(int accountId, String preAuthToken, Attempt attempt) {
        String attemptKey = tokenHasher.hash(preAuthToken);
        if (!check(accountId, attempt)) {
            if (attemptTracker.recordFailure(attemptKey) >= TwoFactorAttemptTracker.MAX_ATTEMPTS) {
                accounts.deleteToken(preAuthToken);
            }
            throw TwoFactorRefusal.TWO_FACTOR_CODE_WRONG.raise();
        }
        attemptTracker.reset(attemptKey);
        accounts.deleteToken(preAuthToken);
    }

    private boolean check(int accountId, Attempt attempt) {
        if ("BACKUP_CODE".equals(attempt.factor())) {
            boolean valid = twoFactorService
                    .verifyBackupCode(accountId, attempt.proof(), attempt.ip())
                    .valid();
            if (valid) audit(accountId, TwoFactorEvent.BACKUP_CODE_USED, TwoFactorKind.BACKUP_CODES, attempt);
            return valid;
        }
        boolean valid = twoFactorService.verifyTotp(accountId, attempt.proof());
        if (valid) audit(accountId, TwoFactorEvent.LOGIN_VERIFIED, TwoFactorKind.TOTP, attempt);
        return valid;
    }

    private void audit(int accountId, TwoFactorEvent event, TwoFactorKind kind, Attempt attempt) {
        auditService.record(accountId, null, event, kind, attempt.userAgent(), attempt.country());
    }

    /**
     * One try at the second factor.
     *
     * @param factor    {@code BACKUP_CODE} for a backup code, anything else for an authenticator code
     * @param proof     the code
     * @param ip        where the try came from
     * @param userAgent the browser it came from
     * @param country   the country the edge placed it in, or null
     */
    public record Attempt(
            String factor,
            String proof,
            String ip,
            @Nullable String userAgent,
            @Nullable String country) {}
}
