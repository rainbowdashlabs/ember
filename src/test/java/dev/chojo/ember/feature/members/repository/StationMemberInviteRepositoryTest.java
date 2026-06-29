/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct repository-level coverage for the cases the service test does not naturally exercise:
 * empty-result lookups, parent / guardian-invite chaining, and the idempotency of mark-accepted
 * + delete.
 */
class StationMemberInviteRepositoryTest extends RepositoryTestBase {

    private static StationMemberInviteRepository repo;
    private static Station station;
    private static Account account;
    private static StationMember inviter;

    @BeforeAll
    static void setup() {
        repo = new StationMemberInviteRepository();
        station = stationRepo.create("Invite Repo Test");
        account = accountRepo.create("invite-repo@test.example", "Invite", "Repo");
        inviter = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static Instant futureExpiry() {
        return Instant.now().plus(7, ChronoUnit.DAYS);
    }

    @Test
    void findByIdMissingReturnsEmpty() {
        assertTrue(repo.findById(99999).isEmpty());
    }

    @Test
    void findByTokenMissingReturnsEmpty() {
        assertTrue(repo.findByToken("does-not-exist").isEmpty());
    }

    @Test
    void existsForStationFalseWhenNoInvites() {
        Station empty = stationRepo.create("No invites here");
        try {
            assertFalse(repo.existsForStation(empty.id()));
        } finally {
            stationRepo.delete(empty.id());
        }
    }

    @Test
    void guardianInvitesAreReturnedForParent() {
        var parent = repo.create(
                station.id(),
                "tok-parent-" + System.nanoTime(),
                "parent@example.com",
                "Pa",
                "Rent",
                StationUserType.MEMBER,
                null,
                null,
                inviter.id(),
                futureExpiry());
        var guardian = repo.create(
                station.id(),
                "tok-guardian-" + System.nanoTime(),
                "guardian@example.com",
                "Guar",
                "Dian",
                StationUserType.MEMBER,
                null,
                parent.id(),
                inviter.id(),
                futureExpiry());

        var found = repo.findGuardianInvitesFor(parent.id());
        assertEquals(1, found.size());
        assertEquals(guardian.id(), found.getFirst().id());

        repo.delete(guardian.id());
        repo.delete(parent.id());
    }

    @Test
    void findPendingByStationExcludesAccepted() {
        var invite = repo.create(
                station.id(),
                "tok-pending-" + System.nanoTime(),
                "pending@example.com",
                "Pen",
                "Ding",
                StationUserType.MEMBER,
                null,
                null,
                inviter.id(),
                futureExpiry());

        long pendingBefore = repo.findPendingByStation(station.id()).stream()
                .filter(i -> i.id() == invite.id())
                .count();
        assertEquals(1, pendingBefore);

        assertTrue(repo.markAccepted(invite.id(), account.id()));
        // second call is a no-op because accepted_at is already set
        assertFalse(repo.markAccepted(invite.id(), account.id()));

        long pendingAfter = repo.findPendingByStation(station.id()).stream()
                .filter(i -> i.id() == invite.id())
                .count();
        assertEquals(0, pendingAfter);

        repo.delete(invite.id());
    }

    @Test
    void deleteOnMissingRowReturnsFalse() {
        assertFalse(repo.delete(987654));
    }
}
