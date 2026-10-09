/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountAction;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AccountReach;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.accountlink.service.LinkAnswerService;
import dev.chojo.ember.feature.accountlink.service.TestAccountLinks;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountRequest;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A member manager who invites somebody else's address and then moves it to an address of their own
 * would hold the account after the next "forgot password". Inviting the address no longer attaches the
 * account, so there is nothing at the station to move it on.
 */
class InvitedAddressTakeoverTest extends RepositoryTestBase {
    private AuthService authService;
    private AccountEmailService emails;
    private MemberAccountService accounts;
    private StationMemberInviteService invites;
    private StationSession manager;
    private Account victim;

    @BeforeEach
    void setup() {
        authService = mock(AuthService.class);
        emails = mock(AccountEmailService.class);
        accounts = new MemberAccountService(
                accountRepo,
                stationMemberRepo,
                authService,
                new LoginNameService(accountRepo),
                emails,
                mock(StepUpGuard.class),
                mock(MemberNameResolver.class),
                mock(NameChangeService.class),
                new AccountReach(accountRepo));
        invites = TestAccountLinks.inviteService(
                accountRepo, stationRepo, stationMemberRepo, newGroupMemberships(), authService);
        var station = stationRepo.create("Takeover " + System.nanoTime());
        var managerAccount = accountRepo.create("takeover-manager-" + System.nanoTime() + "@test.com", "M", "M", true);
        manager = stationSession(
                stationMemberRepo.create(station.id(), managerAccount.id()), StationPermission.MEMBER_EDIT);
        victim = accountRepo.create("takeover-victim-" + System.nanoTime() + "@test.com", "Vera", "Victim", true);
        accountRepo.createCredential(victim.id(), "victims-own-hash");
    }

    @Test
    void anInvitedAddressCannotBeMovedOnByTheStationThatTypedIt() {
        var invited = invites.provision(
                manager.stationId(),
                victim.email(),
                "Vera",
                "Victim",
                StationUserType.MEMBER,
                null,
                SetupMail.SEND_NOW,
                manager.member().id());
        assertNull(invited.accountId());

        var moved = assertThrows(
                RefusalResponse.class,
                () -> accounts.update(
                        manager.user(),
                        manager.stationId(),
                        victim.id(),
                        new UpdateAccountRequest("attacker@evil.test", null, "Vera", "Victim")));
        var reset = assertThrows(
                RefusalResponse.class,
                () -> accounts.actionableAccount(
                        victim.id(),
                        manager,
                        MemberRefusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET,
                        AccountAction.PASSWORD_RESET));

        assertEquals(MemberRefusal.MEMBER_NOT_HERE, moved.refusal());
        assertEquals(MemberRefusal.MEMBER_NOT_HERE, reset.refusal());
        assertEquals(
                victim.email(), accountRepo.findById(victim.id()).orElseThrow().email());
        verify(emails, never()).setEmailFor(anyInt(), anyInt(), anyString());
    }

    /**
     * Once the person accepts, the station reaches the account, but an account that also belongs to the
     * person's own station is still not this station's to move.
     */
    @Test
    void anAcceptedLinkToAnAccountOfAnotherStationStillKeepsItsAddress() {
        var home = stationRepo.create("Takeover home " + System.nanoTime());
        stationMemberRepo.create(home.id(), victim.id());
        var invited = invites.provision(
                manager.stationId(),
                victim.email(),
                "Vera",
                "Victim",
                StationUserType.MEMBER,
                null,
                SetupMail.SEND_NOW,
                manager.member().id());
        var answers = new LinkAnswerService(
                new AccountLinkRepository(),
                stationMemberRepo,
                mock(MemberNameResolver.class),
                new TwoFactorAuditService(twoFactorRepo),
                mock(Notifier.class),
                TokenHasher.forTesting(TestAccountLinks.PEPPER),
                TestAccountLinks.associationAnswers(accountRepo, clusterRepo, twoFactorRepo, mock(Notifier.class)));
        var request = new AccountLinkRepository()
                .findUnansweredForMember(invited.memberId())
                .orElseThrow();

        answers.accept(victim.id(), request.uid(), null, null);

        var moved = assertThrows(
                RefusalResponse.class,
                () -> accounts.update(
                        manager.user(),
                        manager.stationId(),
                        victim.id(),
                        new UpdateAccountRequest("attacker@evil.test", null, "Vera", "Victim")));
        assertEquals(MemberRefusal.ACCOUNT_SHARED_WITH_ANOTHER_STATION, moved.refusal());
        assertEquals(
                victim.email(), accountRepo.findById(victim.id()).orElseThrow().email());
    }
}
