/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AuthService;
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

    @Test
    void provision_creates_account_membership_and_sends_setup_mail() {
        String email = uniqueEmail("alice");
        int groupId = memberGroupRepo.create(station.id(), "Group A").id();

        var result = service.provision(station.id(), email, "Alice", "Apple", StationUserType.TEAM, groupId);

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

        var result = service.provision(station.id(), email, "Other", "Name", StationUserType.MEMBER, null);

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

        service.provision(station.id(), email, "Carol", "Cherry", StationUserType.MEMBER, null);

        verify(authService).sendPasswordSetup(existing.id());
    }

    @Test
    void provision_keeps_existing_membership_untouched() {
        String email = uniqueEmail("dave");
        int groupId = memberGroupRepo.create(station.id(), "Group B").id();
        var first = service.provision(station.id(), email, "Dave", "Damson", StationUserType.MEMBER, null);

        var second = service.provision(station.id(), email, "Dave", "Damson", StationUserType.MANAGER, groupId);

        assertFalse(second.membershipCreated());
        assertEquals(first.memberId(), second.memberId());
        var member = stationMemberRepo.findById(first.memberId()).orElseThrow();
        assertEquals(StationUserType.MEMBER, member.userType());
        assertTrue(memberGroupRepo.findMembers(groupId).isEmpty());
    }

    @Test
    void provision_synthetic_email_creates_account_without_mail() {
        String email = "kid.jones@" + station.id() + ".local";

        var result = service.provision(station.id(), email, "Kid", "Jones", StationUserType.MEMBER, null);

        assertTrue(result.accountCreated());
        verify(authService, never()).sendPasswordSetup(anyInt());
    }

    @Test
    void provision_synthetic_email_never_attaches_existing_account() {
        String email = "twin.jones@" + station.id() + ".local";
        service.provision(station.id(), email, "Twin", "Jones", StationUserType.MEMBER, null);

        assertThrows(
                ProvisionException.class,
                () -> service.provision(station.id(), email, "Other", "Jones", StationUserType.MEMBER, null));
    }

    @Test
    void batch_provisions_guardians_and_links_them_as_manager() {
        String memberEmail = uniqueEmail("junior");
        String guardianEmail = uniqueEmail("parent");

        var result = service.createBatch(
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
        service.provision(station.id(), synthetic, "Same", "Name", StationUserType.MEMBER, null);
        String okEmail = uniqueEmail("fine");

        var result = service.createBatch(
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
        var result = service.createBatch(
                station.id(), List.of(new InviteRequest(email, "Deb", "Default", null, null, List.of())));

        assertEquals(StationUserType.MEMBER, result.provisioned().get(0).userType());
    }

    @Test
    void batch_failed_parent_skips_guardians() {
        String synthetic = "solo.kid@" + station.id() + ".local";
        service.provision(station.id(), synthetic, "Solo", "Kid", StationUserType.MEMBER, null);

        var result = service.createBatch(
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

        var result = service.createBatch(
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
