/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The access changes waiting to be announced: one per member, replaced rather than piled up, and
 * offered only once their waiting time has passed.
 */
class ManagedLoginNoticeRepositoryTest extends RepositoryTestBase {

    private static ManagedLoginNoticeRepository repository;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        repository = new ManagedLoginNoticeRepository();
        station = stationRepo.create("Notice Repository Station");
        account = accountRepo.create("notice-repo@test.com", "Nina", "Notice");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void clear() {
        repository.cancel(member.id());
    }

    @Test
    void aChangeIsFoundAgainWithWhatItAnnounces() {
        Instant due = Instant.now().plus(5, ChronoUnit.MINUTES);

        repository.schedule(member.id(), true, due);

        var waiting = repository.find(member.id()).orElseThrow();
        assertEquals(member.id(), waiting.memberId());
        assertTrue(waiting.granted());
        assertEquals(due.truncatedTo(ChronoUnit.MILLIS), waiting.dueAt().truncatedTo(ChronoUnit.MILLIS));
    }

    @Test
    void aSecondChangeReplacesTheFirst() {
        repository.schedule(member.id(), true, Instant.now().plus(5, ChronoUnit.MINUTES));
        repository.schedule(member.id(), false, Instant.now().plus(9, ChronoUnit.MINUTES));

        assertFalse(repository.find(member.id()).orElseThrow().granted());
    }

    @Test
    void cancellingSaysWhetherAnythingWasWaiting() {
        assertFalse(repository.cancel(member.id()));

        repository.schedule(member.id(), true, Instant.now());

        assertTrue(repository.cancel(member.id()));
        assertTrue(repository.find(member.id()).isEmpty());
    }

    @Test
    void onlyWhatIsDueIsOffered() {
        repository.schedule(member.id(), true, Instant.now().plus(5, ChronoUnit.MINUTES));

        assertTrue(repository.findDue(Instant.now()).stream().noneMatch(due -> due.memberId() == member.id()));

        repository.schedule(member.id(), true, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertTrue(repository.findDue(Instant.now()).stream().anyMatch(due -> due.memberId() == member.id()));
    }

    @Test
    void aMemberWhoIsGoneTakesTheirChangeWithThem() {
        var leavingAccount = accountRepo.create("notice-leaving@test.com", "Lars", "Leaving");
        var leaving = stationMemberRepo.create(station.id(), leavingAccount.id());
        repository.schedule(leaving.id(), true, Instant.now().minus(1, ChronoUnit.MINUTES));

        stationMemberRepo.delete(leaving.id());

        assertTrue(repository.find(leaving.id()).isEmpty());
        accountRepo.delete(leavingAccount.id());
    }
}
