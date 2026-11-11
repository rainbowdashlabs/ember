/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountSetupMail;
import dev.chojo.ember.feature.account.entity.AccountSetupMail.Outcome;
import dev.chojo.ember.feature.account.entity.SetupMailKind;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.MailConfirmationPolicy;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends an account that is not yet ready the mail it is waiting for, on the instance administrator's
 * behalf: the invitation to choose a password for an account that has none of its own, or the request
 * to confirm the address for one that has a password but never confirmed its real address.
 *
 * <p>The refusals are those a station's own resend raises, so the two read alike. Unlike the station's
 * resend, an instance administrator is not bound by which station may reach the account.
 *
 * <p>On an instance that cannot send at all, asking for the confirmation marks the address confirmed
 * instead of sending anything, the same as when the account asks itself; the answer says so.
 */
@Singleton
public class AccountSetupMailService {
    private final AccountRepository accountRepository;
    private final MailRecipientService mailRecipientService;
    private final AuthService authService;
    private final MailConfirmationPolicy confirmationPolicy;

    @Inject
    public AccountSetupMailService(
            AccountRepository accountRepository,
            MailRecipientService mailRecipientService,
            AuthService authService,
            MailConfirmationPolicy confirmationPolicy) {
        this.accountRepository = accountRepository;
        this.mailRecipientService = mailRecipientService;
        this.authService = authService;
        this.confirmationPolicy = confirmationPolicy;
    }

    /**
     * Sends the mail the account is waiting for.
     *
     * @param accountId the account
     * @return which mail went out, or that the address was marked confirmed without one
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when the account does not exist, needs neither
     *                                                     mail, or nobody can be written to about it
     */
    public AccountSetupMail send(int accountId) {
        Account account =
                accountRepository.findById(accountId).orElseThrow(MemberRefusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL::raise);
        boolean awaitsSetup = account.setupCompletedAt() == null && !accountRepository.hasChosenPassword(accountId);
        SetupMailKind kind = SetupMailKind.of(awaitsSetup, account.hasRealEmail(), account.emailVerified());
        if (kind == null) {
            throw MemberRefusal.ACCOUNT_ALREADY_SET_UP.raise();
        }
        if (kind == SetupMailKind.INVITATION) {
            if (!mailRecipientService.isReachable(accountId)) {
                throw MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise();
            }
            authService.sendPasswordSetup(accountId);
            return new AccountSetupMail(Outcome.INVITATION_SENT);
        }
        boolean confirmedWithoutMail = confirmationPolicy.confirmationCountsAsGranted();
        authService.resendVerification(account.email());
        return new AccountSetupMail(confirmedWithoutMail ? Outcome.CONFIRMED : Outcome.CONFIRMATION_SENT);
    }
}
