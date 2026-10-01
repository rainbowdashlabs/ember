/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.members.entity.StationMember;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Sends a member the invitation to set up their account once more, for when the first one was
 * lost.
 */
@Singleton
public class MemberSetupMailService {
    private final AccountRepository accountRepository;
    private final MailRecipientService mailRecipientService;
    private final AuthService authService;

    @Inject
    public MemberSetupMailService(
            AccountRepository accountRepository, MailRecipientService mailRecipientService, AuthService authService) {
        this.accountRepository = accountRepository;
        this.mailRecipientService = mailRecipientService;
        this.authService = authService;
    }

    /**
     * Sends the setup mail again.
     *
     * <p>Refused for an account that is already set up, by the same rule the member list draws its
     * hourglass by: a password the person chose is as good as a sign-in, and either one makes a
     * second invitation pointless. Refused as well where no mail about the account can arrive.
     *
     * @param member the member, already checked to belong to the caller's station
     */
    public void resend(StationMember member) {
        Integer accountId = member.accountId();
        if (accountId == null) {
            throw Refusal.MEMBER_HAS_NO_ACCOUNT.raise();
        }
        var account = accountRepository.findById(accountId).orElseThrow(Refusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL::raise);
        if (account.setupCompletedAt() != null || accountRepository.hasChosenPassword(account.id())) {
            throw Refusal.ACCOUNT_ALREADY_SET_UP.raise();
        }
        if (!mailRecipientService.isReachable(account.id())) {
            throw Refusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise();
        }
        authService.sendPasswordSetup(account.id());
    }
}
