/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.StationMemberInvite;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.AcceptException;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.GuardianRequest;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.InviteRequest;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

class StationMemberInviteServiceTest extends RepositoryTestBase {

    private static EmailService emailService;
    private static StationMemberInviteService service;

    private Station station;
    private StationMember inviter;

    @BeforeAll
    static void setupServices() {
        emailService = mock(EmailService.class);
        doNothing()
                .when(emailService)
                .sendStationMemberInviteEmail(any(), any(), any(), any(), any(), any(), any(), anyInt());
        service = new StationMemberInviteService(
                stationMemberInviteRepo,
                stationMemberRepo,
                memberGroupRepo,
                accountRepo,
                stationRepo,
                emailService,
                new PasswordHasher());
    }

    @BeforeEach
    void freshFixture() {
        station = stationRepo.create("Invite Station " + System.nanoTime());
        Account inviterAccount = accountRepo.create("inviter-" + System.nanoTime() + "@test.com", "Sam", "Smith", true);
        inviter = stationMemberRepo.create(station.id(), inviterAccount.id());
    }

    @Test
    void create_persists_invite_and_dispatches_one_email() {
        var created = service.createBatch(
                station.id(),
                inviter.id(),
                List.of(new InviteRequest(
                        "alice-" + System.nanoTime() + "@test.com",
                        "Alice",
                        "Apple",
                        StationUserType.MEMBER,
                        null,
                        List.of())));

        assertEquals(1, created.size());
        assertTrue(stationMemberInviteRepo.existsForStation(station.id()));
    }

    @Test
    void batch_expands_guardian_rows_into_pointing_guardian_invites() {
        var created = service.createBatch(
                station.id(),
                inviter.id(),
                List.of(new InviteRequest(
                        "child-" + System.nanoTime() + "@test.com",
                        "Child",
                        "Doe",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest("guard-" + System.nanoTime() + "@test.com", "Guard", "Doe")))));

        assertEquals(2, created.size());
        var parent = created.get(0);
        var guardian = created.get(1);
        assertEquals(StationUserType.MEMBER, parent.userType());
        assertEquals(StationUserType.GUARDIAN, guardian.userType());
        assertEquals(parent.id(), guardian.guardianOfInviteId());
    }

    @Test
    void revoke_pending_invite_removes_it_but_refuses_after_accept() {
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "rev-" + System.nanoTime() + "@test.com",
                "Rev",
                "Oker",
                StationUserType.MEMBER,
                null,
                null);
        assertTrue(service.revoke(station.id(), invite.id()));
        assertFalse(service.revoke(station.id(), invite.id()));

        var keep = service.invite(
                station.id(),
                inviter.id(),
                "keep-" + System.nanoTime() + "@test.com",
                "Keep",
                "Er",
                StationUserType.MEMBER,
                null,
                null);
        service.accept(keep.token(), "Strong-Password123!");
        assertFalse(service.revoke(station.id(), keep.id()));
    }

    @Test
    void accept_creates_account_member_and_marks_invite() {
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "fresh-" + System.nanoTime() + "@test.com",
                "Fresh",
                "User",
                StationUserType.MEMBER,
                null,
                null);

        var result = service.accept(invite.token(), "Strong-Password123!");

        assertNotNull(accountRepo.findById(result.accountId()).orElse(null));
        var member = stationMemberRepo.findById(result.memberId()).orElseThrow();
        assertEquals(StationUserType.MEMBER, member.userType());

        var reloaded = stationMemberInviteRepo.findById(invite.id()).orElseThrow();
        assertTrue(reloaded.isAccepted());
        assertEquals(result.accountId(), reloaded.acceptedAccountId());
    }

    @Test
    void accept_with_group_id_adds_member_to_group() {
        var group = memberGroupRepo.create(station.id(), "Crew");
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "crew-" + System.nanoTime() + "@test.com",
                "Crew",
                "Member",
                StationUserType.MEMBER,
                group.id(),
                null);

        var result = service.accept(invite.token(), "Strong-Password123!");

        var groups = memberGroupRepo.findGroupsForMember(result.memberId());
        assertEquals(1, groups.size());
        assertEquals(group.id(), groups.getFirst().id());
    }

    @Test
    void guardian_link_works_when_guardian_accepts_before_parent() {
        long suffix = System.nanoTime();
        var batch = service.createBatch(
                station.id(),
                inviter.id(),
                List.of(new InviteRequest(
                        "child-" + suffix + "@test.com",
                        "Child",
                        "Doe",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest("guard-" + suffix + "@test.com", "Guard", "Doe")))));
        var parent = batch.get(0);
        var guardian = batch.get(1);

        var guardianResult = service.accept(guardian.token(), "Strong-Password123!");
        var parentResult = service.accept(parent.token(), "Strong-Password123!");

        var managers = stationMemberRepo.findManagers(parentResult.memberId());
        assertEquals(1, managers.size());
        assertEquals(guardianResult.memberId(), managers.getFirst().id());
    }

    @Test
    void guardian_link_works_when_parent_accepts_before_guardian() {
        long suffix = System.nanoTime();
        var batch = service.createBatch(
                station.id(),
                inviter.id(),
                List.of(new InviteRequest(
                        "child-" + suffix + "@test.com",
                        "Child",
                        "Doe",
                        StationUserType.MEMBER,
                        null,
                        List.of(new GuardianRequest("guard-" + suffix + "@test.com", "Guard", "Doe")))));
        var parent = batch.get(0);
        var guardian = batch.get(1);

        var parentResult = service.accept(parent.token(), "Strong-Password123!");
        var guardianResult = service.accept(guardian.token(), "Strong-Password123!");

        var managers = stationMemberRepo.findManagers(parentResult.memberId());
        assertEquals(1, managers.size());
        assertEquals(guardianResult.memberId(), managers.getFirst().id());
    }

    @Test
    void accept_rejects_expired_invite() {
        var invite = stationMemberInviteRepo.create(
                station.id(),
                "expired-" + System.nanoTime(),
                "exp@test.com",
                "Ex",
                "Pired",
                StationUserType.MEMBER,
                null,
                null,
                inviter.id(),
                Instant.now().minus(Duration.ofDays(1)));
        var e = assertThrows(AcceptException.class, () -> service.accept(invite.token(), "Strong-Password123!"));
        assertEquals(AcceptException.Reason.EXPIRED, e.reason());
    }

    @Test
    void accept_rejects_unknown_token() {
        var e = assertThrows(AcceptException.class, () -> service.accept("nope-" + System.nanoTime(), "Strong!Pw123!"));
        assertEquals(AcceptException.Reason.NOT_FOUND, e.reason());
    }

    @Test
    void accept_rejects_weak_password() {
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "weak-" + System.nanoTime() + "@test.com",
                "Weak",
                "Pw",
                StationUserType.MEMBER,
                null,
                null);
        var e = assertThrows(AcceptException.class, () -> service.accept(invite.token(), "weak"));
        assertEquals(AcceptException.Reason.WEAK_PASSWORD, e.reason());
    }

    @Test
    void accept_rejects_already_used_invite() {
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "dup-" + System.nanoTime() + "@test.com",
                "Dup",
                "Lic",
                StationUserType.MEMBER,
                null,
                null);
        service.accept(invite.token(), "Strong-Password123!");
        var e = assertThrows(AcceptException.class, () -> service.accept(invite.token(), "Strong-Password123!"));
        assertEquals(AcceptException.Reason.ALREADY_ACCEPTED, e.reason());
    }

    @Test
    void accept_rejects_email_already_in_use() {
        String email = "dup-email-" + System.nanoTime() + "@test.com";
        accountRepo.create(email, "Existing", "Account", true);
        var invite =
                service.invite(station.id(), inviter.id(), email, "New", "User", StationUserType.MEMBER, null, null);
        var e = assertThrows(AcceptException.class, () -> service.accept(invite.token(), "Strong-Password123!"));
        assertEquals(AcceptException.Reason.EMAIL_IN_USE, e.reason());
    }

    @Test
    void list_returns_invites_for_station_only() {
        Station other = stationRepo.create("Other " + System.nanoTime());
        Account otherAcct = accountRepo.create("other-" + System.nanoTime() + "@test.com", "Other", "Inviter", true);
        StationMember otherMember = stationMemberRepo.create(other.id(), otherAcct.id());
        service.invite(
                station.id(),
                inviter.id(),
                "one-" + System.nanoTime() + "@test.com",
                "One",
                "x",
                StationUserType.MEMBER,
                null,
                null);
        service.invite(
                other.id(),
                otherMember.id(),
                "two-" + System.nanoTime() + "@test.com",
                "Two",
                "x",
                StationUserType.MEMBER,
                null,
                null);

        assertEquals(1, service.listForStation(station.id()).size());
        assertEquals(1, service.listForStation(other.id()).size());
    }

    @Test
    void revoke_rejects_cross_station_id() {
        var invite = service.invite(
                station.id(),
                inviter.id(),
                "cross-" + System.nanoTime() + "@test.com",
                "Cross",
                "Stn",
                StationUserType.MEMBER,
                null,
                null);
        Station other = stationRepo.create("Cross " + System.nanoTime());
        assertFalse(service.revoke(other.id(), invite.id()));
        assertNull(stationMemberInviteRepo
                .findById(invite.id())
                .map(StationMemberInvite::acceptedAt)
                .orElse(null));
    }
}
