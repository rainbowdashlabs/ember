/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.GuardianRequest;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.InviteRequest;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.ProvisionException;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class StationMemberInviteServiceTest extends RepositoryTestBase {

    private StationMemberInviteService service;
    private AuthService authService;

    private Station station;

    @BeforeEach
    void freshFixture() {
        authService = mock(AuthService.class);
        service = new StationMemberInviteService(
                stationMemberRepo, memberGroupRepo, new AccountInviteService(accountRepo, authService));
        station = stationRepo.create("Invite Station " + System.nanoTime());
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + System.nanoTime() + "@test.com";
    }

    private StationMemberInviteService.ProvisionedMember provision(
            int stationId, String email, String firstName, String lastName, StationUserType userType, Integer groupId) {
        return service.provision(stationId, email, firstName, lastName, userType, groupId, SetupMail.SEND_NOW);
    }

    private StationMemberInviteService.BatchResult createBatch(int stationId, List<InviteRequest> requests) {
        return service.createBatch(stationId, requests, SetupMail.SEND_NOW);
    }

    /**
     * Somebody entered as an ordinary member stays one. Making a guardian a guardian is a decision
     * of the screen that enters one, and pushing it down to here would turn every member the
     * station writes down into somebody who may sign in and answer for other people.
     */
    @Test
    void provision_leaves_an_ordinary_member_an_ordinary_member() {
        String email = uniqueEmail("plain");

        var result = provision(station.id(), email, "Paul", "Plain", StationUserType.MEMBER, null);

        var member = stationMemberRepo.findById(result.memberId()).orElseThrow();
        assertEquals(StationUserType.MEMBER, member.userType());
        assertFalse(memberPermissionResolver.resolve(member.id()).contains(StationPermission.LOGIN));
        assertFalse(memberPermissionResolver.resolve(member.id()).contains(StationPermission.MEMBER_GUARDIAN));
    }

    /** A guardian carries the right to sign in, which is the whole point of being one. */
    @Test
    void a_guardian_may_sign_in_and_answer_for_others() {
        String email = uniqueEmail("guard");

        var result = provision(station.id(), email, "Gerda", "Guard", StationUserType.GUARDIAN, null);

        var member = stationMemberRepo.findById(result.memberId()).orElseThrow();
        assertEquals(StationUserType.GUARDIAN, member.userType());
        var held = memberPermissionResolver.resolve(member.id());
        assertTrue(held.contains(StationPermission.LOGIN));
        assertTrue(held.contains(StationPermission.MEMBER_GUARDIAN));
    }

    @Test
    void provision_creates_account_membership_and_sends_setup_mail() {
        String email = uniqueEmail("alice");
        int groupId = memberGroupRepo.create(station.id(), "Group A").id();

        var result = provision(station.id(), email, "Alice", "Apple", StationUserType.TEAM, groupId);

        assertTrue(result.accountCreated());
        assertTrue(result.membershipCreated());
        Account account = accountRepo.findByEmail(email).orElseThrow();
        assertTrue(account.emailVerified());
        var member = stationMemberRepo
                .findByStationAndAccount(station.id(), account.id())
                .orElseThrow();
        assertEquals(StationUserType.TEAM, member.userType());
        assertTrue(memberGroupRepo.findMembers(groupId).stream().anyMatch(m -> m.id() == member.id()));
        verify(authService).sendPasswordSetup(account.id());
    }

    @Test
    void provision_attaches_existing_account_without_touching_it() {
        String email = uniqueEmail("bob");
        Account existing = accountRepo.create(email, "Bob", "Berry", true);
        accountRepo.createCredential(existing.id(), "hash");

        var result = provision(station.id(), email, "Other", "Name", StationUserType.MEMBER, null);

        assertFalse(result.accountCreated());
        assertTrue(result.membershipCreated());
        Account reloaded = accountRepo.findById(existing.id()).orElseThrow();
        assertEquals("Bob", reloaded.firstName());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void provision_resends_setup_mail_for_unclaimed_existing_account() {
        String email = uniqueEmail("carol");
        Account existing = accountRepo.create(email, "Carol", "Cherry", true);

        provision(station.id(), email, "Carol", "Cherry", StationUserType.MEMBER, null);

        verify(authService).sendPasswordSetup(existing.id());
    }

    @Test
    void provision_keeps_existing_membership_untouched() {
        String email = uniqueEmail("dave");
        int groupId = memberGroupRepo.create(station.id(), "Group B").id();
        var first = provision(station.id(), email, "Dave", "Damson", StationUserType.MEMBER, null);

        var second = provision(station.id(), email, "Dave", "Damson", StationUserType.MANAGER, groupId);

        assertFalse(second.membershipCreated());
        assertEquals(first.memberId(), second.memberId());
        var member = stationMemberRepo.findById(first.memberId()).orElseThrow();
        assertEquals(StationUserType.MEMBER, member.userType());
        assertTrue(memberGroupRepo.findMembers(groupId).isEmpty());
    }

    @Test
    void provision_synthetic_email_creates_account_without_mail() {
        String email = "kid.jones@" + station.id() + ".local";

        var result = provision(station.id(), email, "Kid", "Jones", StationUserType.MEMBER, null);

        assertTrue(result.accountCreated());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void provision_holds_the_setup_mail_back_when_it_was_not_asked_for() {
        String email = uniqueEmail("later");

        var result =
                service.provision(station.id(), email, "Lena", "Later", StationUserType.MEMBER, null, SetupMail.LATER);

        assertTrue(result.accountCreated());
        Account account = accountRepo.findByEmail(email).orElseThrow();
        assertFalse(accountRepo.deleteTokensByAccountAndType(account.id(), TokenType.SET_PASSWORD));
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void a_member_entered_without_a_mail_can_still_be_sent_one_afterwards() {
        String email = uniqueEmail("afterwards");
        var result = service.provision(
                station.id(), email, "Nina", "Nachher", StationUserType.MEMBER, null, SetupMail.LATER);
        Account account = accountRepo.findById(result.accountId()).orElseThrow();

        assertNull(account.setupCompletedAt());
        assertFalse(accountRepo.hasChosenPassword(account.id()));
    }

    @Test
    void batch_holds_every_setup_mail_back_when_it_was_not_asked_for() {
        String memberEmail = uniqueEmail("junior-later");
        String guardianEmail = uniqueEmail("parent-later");

        var result = service.createBatch(
                station.id(),
                List.of(new InviteRequest(
                        memberEmail,
                        "Junior",
                        "Later",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest(guardianEmail, "Parent", "Later")))),
                SetupMail.LATER);

        assertEquals(2, result.provisioned().size());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void provision_synthetic_email_never_attaches_existing_account() {
        String email = "twin.jones@" + station.id() + ".local";
        provision(station.id(), email, "Twin", "Jones", StationUserType.MEMBER, null);

        assertThrows(
                ProvisionException.class,
                () -> provision(station.id(), email, "Other", "Jones", StationUserType.MEMBER, null));
    }

    @Test
    void batch_provisions_guardians_and_links_them_as_manager() {
        String memberEmail = uniqueEmail("junior");
        String guardianEmail = uniqueEmail("parent");

        var result = createBatch(
                station.id(),
                List.of(new InviteRequest(
                        memberEmail,
                        "Junior",
                        "Jones",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest(guardianEmail, "Parent", "Jones")))));

        assertTrue(result.failed().isEmpty());
        assertEquals(2, result.provisioned().size());
        var junior = result.provisioned().get(0);
        var parent = result.provisioned().get(1);
        assertEquals(StationUserType.GUARDIAN, parent.userType());
        assertTrue(
                stationMemberRepo.findManagers(junior.memberId()).stream().anyMatch(m -> m.id() == parent.memberId()));
        verify(authService, times(2)).sendPasswordSetup(anyInt());
    }

    @Test
    void batch_reports_failed_entries_and_continues() {
        String synthetic = "same.name@" + station.id() + ".local";
        provision(station.id(), synthetic, "Same", "Name", StationUserType.MEMBER, null);
        String okEmail = uniqueEmail("fine");

        var result = createBatch(
                station.id(),
                List.of(
                        new InviteRequest(synthetic, "Same", "Name", StationUserType.MEMBER, null, List.of()),
                        new InviteRequest(okEmail, "Fine", "Fellow", StationUserType.MEMBER, null, List.of())));

        assertEquals(1, result.failed().size());
        assertEquals(synthetic, result.failed().get(0).email());
        assertEquals(1, result.provisioned().size());
        assertEquals(okEmail, result.provisioned().get(0).email());
    }

    @Test
    void batch_defaults_user_type_to_member() {
        String email = uniqueEmail("default");
        var result =
                createBatch(station.id(), List.of(new InviteRequest(email, "Deb", "Default", null, null, List.of())));

        assertEquals(StationUserType.MEMBER, result.provisioned().get(0).userType());
    }

    @Test
    void batch_failed_parent_skips_guardians() {
        String synthetic = "solo.kid@" + station.id() + ".local";
        provision(station.id(), synthetic, "Solo", "Kid", StationUserType.MEMBER, null);

        var result = createBatch(
                station.id(),
                List.of(new InviteRequest(
                        synthetic,
                        "Solo",
                        "Kid",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest(uniqueEmail("ghost"), "Ghost", "Guardian")))));

        assertEquals(1, result.failed().size());
        assertTrue(result.provisioned().isEmpty());
    }

    @Test
    void batch_guardian_with_existing_account_is_linked_without_new_account() {
        String guardianEmail = uniqueEmail("known-parent");
        Account existing = accountRepo.create(guardianEmail, "Known", "Parent", true);
        accountRepo.createCredential(existing.id(), "hash");

        var result = createBatch(
                station.id(),
                List.of(new InviteRequest(
                        uniqueEmail("kid"),
                        "Kid",
                        "Kiddo",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest(guardianEmail, "Known", "Parent")))));

        assertTrue(result.failed().isEmpty());
        var parent = result.provisioned().get(1);
        assertFalse(parent.accountCreated());
        assertEquals(existing.id(), parent.accountId());
        assertTrue(stationMemberRepo.findManagers(result.provisioned().get(0).memberId()).stream()
                .anyMatch(m -> m.id() == parent.memberId()));
    }
}
