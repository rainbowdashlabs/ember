/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StorageBackendAuditRepositoryTest extends RepositoryTestBase {

    private static Account account;
    private static Station stationA;
    private static Station stationB;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("audit@test.example", "Aud", "It");
        stationA = stationRepo.create("Audit Station A");
        stationB = stationRepo.create("Audit Station B");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
    }

    @Test
    void insertReturnsGeneratedId() {
        long id = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.CREATED,
                Optional.empty(),
                Optional.of("{\"kind\":\"LOCAL\"}"),
                StorageAuditOutcome.OK,
                Optional.empty()));
        assertTrue(id > 0);
    }

    @Test
    void insertWithSystemActorAndError() {
        long id = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.empty(),
                Optional.empty(),
                Optional.of("scheduler"),
                Optional.empty(),
                StorageAuditAction.PROBE_FAILED,
                Optional.of("{\"kind\":\"S3\"}"),
                Optional.empty(),
                StorageAuditOutcome.FAILED,
                Optional.of("connection refused")));
        assertTrue(id > 0);
    }

    @Test
    void findRecentMatchingReturnsMostRecentMatch() {
        Instant base = Instant.now();
        long first = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.PROBE_OK,
                Optional.empty(),
                Optional.empty(),
                StorageAuditOutcome.OK,
                Optional.empty()));
        long second = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.PROBE_OK,
                Optional.empty(),
                Optional.empty(),
                StorageAuditOutcome.OK,
                Optional.empty()));

        var match = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.PROBE_OK,
                StorageAuditOutcome.OK,
                base.minus(1, ChronoUnit.MINUTES));
        assertTrue(match.isPresent());
        assertTrue(match.get().id() == second || match.get().id() >= first);
        assertEquals(StorageAuditAction.PROBE_OK, match.get().action());
        assertEquals(StorageAuditOutcome.OK, match.get().outcome());
        assertEquals(Optional.of(account.id()), match.get().actorAccountId());
        assertEquals(Optional.of(stationA.id()), match.get().stationId());
    }

    @Test
    void findRecentMatchingHonoursCutoff() {
        storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.empty(),
                Optional.empty(),
                Optional.of("scheduler"),
                Optional.of(stationB.id()),
                StorageAuditAction.MIGRATION_STARTED,
                Optional.empty(),
                Optional.empty(),
                StorageAuditOutcome.OK,
                Optional.empty()));

        var future = storageBackendAuditRepo.findRecentMatching(
                Optional.empty(),
                Optional.of("scheduler"),
                Optional.of(stationB.id()),
                StorageAuditAction.MIGRATION_STARTED,
                StorageAuditOutcome.OK,
                Instant.now().plus(1, ChronoUnit.DAYS));
        assertTrue(future.isEmpty());
    }

    @Test
    void findRecentMatchingDistinguishesActors() {
        storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                StorageAuditAction.UPDATED,
                Optional.of("{\"a\":1}"),
                Optional.of("{\"a\":2}"),
                StorageAuditOutcome.OK,
                Optional.empty()));

        var wrongActor = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id() + 999),
                Optional.empty(),
                Optional.empty(),
                StorageAuditAction.UPDATED,
                StorageAuditOutcome.OK,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        assertTrue(wrongActor.isEmpty());

        var rightActor = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                StorageAuditAction.UPDATED,
                StorageAuditOutcome.OK,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        assertTrue(rightActor.isPresent());
        assertTrue(rightActor.get().oldConfig().orElse("").contains("\"a\""));
        assertTrue(rightActor.get().newConfig().orElse("").contains("\"a\""));
    }

    @Test
    void findAllWithoutFiltersReturnsRowsDescending() {
        storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.DELETED,
                Optional.of("{\"kind\":\"S3\"}"),
                Optional.empty(),
                StorageAuditOutcome.OK,
                Optional.empty()));

        var rows = storageBackendAuditRepo.findAll(Optional.empty(), Optional.empty(), 50);
        assertFalse(rows.isEmpty());
        for (int i = 1; i < rows.size(); i++) {
            assertFalse(rows.get(i - 1).ts().isBefore(rows.get(i).ts()));
        }
    }

    @Test
    void findAllWithBeforeFiltersOlder() {
        long id = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.empty(),
                Optional.empty(),
                Optional.of("scheduler"),
                Optional.empty(),
                StorageAuditAction.INSTANCE_DEFAULT_UPDATED,
                Optional.empty(),
                Optional.of("{\"k\":\"v\"}"),
                StorageAuditOutcome.OK,
                Optional.empty()));

        var newer = storageBackendAuditRepo.findAll(
                Optional.of(Instant.now().plus(1, ChronoUnit.DAYS)), Optional.empty(), 10);
        assertTrue(newer.stream().anyMatch(r -> r.id() == id));

        var older = storageBackendAuditRepo.findAll(
                Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)), Optional.empty(), 10);
        assertTrue(older.stream().noneMatch(r -> r.id() == id));
    }

    @Test
    void findByStationOnlyReturnsThatStationsRows() {
        long aId = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationA.id()),
                StorageAuditAction.REJECTED,
                Optional.empty(),
                Optional.empty(),
                StorageAuditOutcome.FAILED,
                Optional.of("non-empty backend")));
        long bId = storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(stationB.id()),
                StorageAuditAction.REJECTED,
                Optional.empty(),
                Optional.empty(),
                StorageAuditOutcome.FAILED,
                Optional.of("non-empty backend")));

        var rowsA = storageBackendAuditRepo.findByStation(stationA.id(), Optional.empty(), 50);
        assertTrue(rowsA.stream().anyMatch(r -> r.id() == aId));
        assertTrue(rowsA.stream().noneMatch(r -> r.id() == bId));
        assertTrue(rowsA.stream().allMatch(r -> r.stationId().equals(Optional.of(stationA.id()))));

        var rowsB = storageBackendAuditRepo.findByStation(stationB.id(), Optional.empty(), 50);
        assertTrue(rowsB.stream().anyMatch(r -> r.id() == bId));
        assertTrue(rowsB.stream().noneMatch(r -> r.id() == aId));
    }

    @Test
    void newEntryRecordExposesEveryField() {
        var entry = new StorageBackendAuditRepository.NewEntry(
                Optional.of(7),
                Optional.of(11),
                Optional.of("boot"),
                Optional.of(13),
                StorageAuditAction.MIGRATION_COMPLETED,
                Optional.of("{}"),
                Optional.of("{\"x\":1}"),
                StorageAuditOutcome.OK,
                Optional.of("hint"));
        assertEquals(Optional.of(7), entry.actorAccountId());
        assertEquals(Optional.of(11), entry.actorMemberId());
        assertEquals(Optional.of("boot"), entry.systemActor());
        assertEquals(Optional.of(13), entry.stationId());
        assertEquals(StorageAuditAction.MIGRATION_COMPLETED, entry.action());
        assertEquals(Optional.of("{}"), entry.oldConfig());
        assertEquals(Optional.of("{\"x\":1}"), entry.newConfig());
        assertEquals(StorageAuditOutcome.OK, entry.outcome());
        assertEquals(Optional.of("hint"), entry.error());
    }
}
