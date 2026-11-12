/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSetupMail.Outcome;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The setup mail an instance administrator sends from the account list: the invitation for an account
 * without a password of its own, the confirmation for one with an unconfirmed real address, and the
 * station resend's refusals for everything else.
 */
class AccountSetupMailServiceTest {
    private static final int ID = 9;

    private AccountRepository accounts;
    private MailRecipientService recipients;
    private AuthService auth;
    private MailConfirmationPolicy policy;
    private AccountSetupMailService service;

    private static Account account(String email, boolean verified, @Nullable Instant setupCompletedAt) {
        return new Account(
                ID,
                UUID.randomUUID(),
                email,
                null,
                "Mara",
                "Nager",
                verified,
                InstanceUserType.USER,
                "Mara Nager",
                null,
                setupCompletedAt);
    }

    private Refusal refusal() {
        return assertThrows(RefusalResponse.class, () -> service.send(ID)).refusal();
    }

    @BeforeEach
    void setup() {
        accounts = mock(AccountRepository.class);
        recipients = mock(MailRecipientService.class);
        auth = mock(AuthService.class);
        policy = mock(MailConfirmationPolicy.class);
        service = new AccountSetupMailService(accounts, recipients, auth, policy);
    }

    @Test
    void anAccountWithoutAPasswordOfItsOwnIsInvitedAgain() {
        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@test.com", true, null)));
        when(recipients.isReachable(ID)).thenReturn(true);

        assertEquals(Outcome.INVITATION_SENT, service.send(ID).outcome());

        verify(auth).sendPasswordSetup(ID);
        verify(auth, never()).resendVerification(anyString());
    }

    @Test
    void anAccountWithAPasswordAndAnUnconfirmedAddressIsAskedToConfirmIt() {
        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@test.com", false, Instant.EPOCH)));
        when(accounts.hasChosenPassword(ID)).thenReturn(true);

        assertEquals(Outcome.CONFIRMATION_SENT, service.send(ID).outcome());

        verify(auth).resendVerification("mara@test.com");
        verify(auth, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void anInstanceThatCannotSendSaysTheAddressWasMarkedConfirmed() {
        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@test.com", false, Instant.EPOCH)));
        when(accounts.hasChosenPassword(ID)).thenReturn(true);
        when(policy.confirmationCountsAsGranted()).thenReturn(true);

        assertEquals(Outcome.CONFIRMED, service.send(ID).outcome());

        verify(auth).resendVerification("mara@test.com");
    }

    @Test
    void anAccountThatNeedsNeitherIsRefused() {
        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@test.com", true, Instant.EPOCH)));
        when(accounts.hasChosenPassword(ID)).thenReturn(true);
        assertEquals(MemberRefusal.ACCOUNT_ALREADY_SET_UP, refusal());

        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@station.local", false, Instant.EPOCH)));
        assertEquals(MemberRefusal.ACCOUNT_ALREADY_SET_UP, refusal(), "a stand-in address has nothing to confirm");

        verify(auth, never()).sendPasswordSetup(anyInt());
        verify(auth, never()).resendVerification(anyString());
    }

    @Test
    void anUnknownAccountOrOneNobodyCanBeWrittenToIsRefused() {
        assertEquals(MemberRefusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL, refusal());

        when(accounts.findById(ID)).thenReturn(Optional.of(account("mara@station.local", false, null)));
        when(recipients.isReachable(ID)).thenReturn(false);
        assertEquals(MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO, refusal());

        verify(auth, never()).sendPasswordSetup(anyInt());
    }
}
