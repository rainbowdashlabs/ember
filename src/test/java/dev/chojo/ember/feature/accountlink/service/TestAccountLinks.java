/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GroupMembershipService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.transfer.ImportedAccountLinks;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;

import static org.mockito.Mockito.mock;

/**
 * Link requests against the real tables, for the tests of the features that ask for them. Nothing is
 * mailed: the mail service is a mock that says the installation sends no mail.
 */
public final class TestAccountLinks {
    /** The pepper the test token hasher uses. */
    public static final String PEPPER = "account-link-pepper";

    private TestAccountLinks() {}

    /**
     * @return a link service over the real tables that mails nothing
     */
    public static AccountLinkService service(
            AccountRepository accounts, StationRepository stations, StationMemberRepository members) {
        return new AccountLinkService(
                new AccountLinkRepository(),
                accounts,
                stations,
                members,
                mock(EmailService.class),
                new MailLocaleService(accounts, new ApplicationSettingRepository()),
                TokenHasher.forTesting(PEPPER));
    }

    /**
     * @return the station's invitations over the real tables, asking through a link service that mails
     * nothing
     */
    public static StationMemberInviteService inviteService(
            AccountRepository accounts,
            StationRepository stations,
            StationMemberRepository members,
            GroupMembershipService groups,
            AuthService auth) {
        return new StationMemberInviteService(
                members, groups, new AccountInviteService(accounts, auth), service(accounts, stations, members));
    }

    /**
     * @return what an import asks with, over the real tables
     */
    public static ImportedAccountLinks importedLinks(
            AccountRepository accounts, StationRepository stations, StationMemberRepository members) {
        return new ImportedAccountLinks(service(accounts, stations, members));
    }
}
