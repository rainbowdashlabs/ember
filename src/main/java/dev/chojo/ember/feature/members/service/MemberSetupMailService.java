/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountReach;
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
    private final AccountReach accountReach;

    @Inject
    public MemberSetupMailService(
            AccountRepository accountRepository,
            MailRecipientService mailRecipientService,
            AuthService authService,
            AccountReach accountReach) {
        this.accountRepository = accountRepository;
        this.mailRecipientService = mailRecipientService;
        this.authService = authService;
        this.accountReach = accountReach;
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
            throw MemberRefusal.MEMBER_HAS_NO_ACCOUNT.raise();
        }
        var account =
                accountRepository.findById(accountId).orElseThrow(MemberRefusal.ACCOUNT_NOT_HERE_ON_SETUP_MAIL::raise);
        accountReach.require(member.stationId(), accountId, AccountAction.SETUP_MAIL);
        if (account.setupCompletedAt() != null || accountRepository.hasChosenPassword(account.id())) {
            throw MemberRefusal.ACCOUNT_ALREADY_SET_UP.raise();
        }
        if (!mailRecipientService.isReachable(account.id())) {
            throw MemberRefusal.ACCOUNT_NOBODY_TO_WRITE_TO.raise();
        }
        authService.sendPasswordSetup(account.id());
    }
}
