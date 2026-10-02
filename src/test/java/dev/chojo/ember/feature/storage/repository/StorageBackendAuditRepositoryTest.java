/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.Cluster;
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
    private static Cluster cluster;

    @BeforeAll
    static void setup() {
        account = accountRepo.create("audit@test.example", "Aud", "It");
        accountRepo.setInstanceUserType(account.id(), InstanceUserType.ADMINISTRATOR);
        stationA = stationRepo.create("Audit Station A");
        stationB = stationRepo.create("Audit Station B");
        cluster = clusterService.create("Audit Kreisverband", null);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(stationA.id());
        stationRepo.delete(stationB.id());
        clusterService.delete(cluster.id());
    }

    /** One row, with every column the caller may leave out left out unless named. */
    private record Row(
            Optional<Integer> account,
            Optional<String> system,
            Optional<Integer> station,
            Optional<Integer> clusterId,
            StorageAuditAction action,
            Optional<String> old,
            Optional<String> fresh,
            StorageAuditOutcome outcome,
            Optional<String> error) {
        static Row by(Account account, StorageAuditAction action) {
            return new Row(
                    Optional.of(account.id()),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    action,
                    Optional.empty(),
                    Optional.empty(),
                    StorageAuditOutcome.OK,
                    Optional.empty());
        }

        static Row bySystem(String name, StorageAuditAction action) {
            return new Row(
                    Optional.empty(),
                    Optional.of(name),
                    Optional.empty(),
                    Optional.empty(),
                    action,
                    Optional.empty(),
                    Optional.empty(),
                    StorageAuditOutcome.OK,
                    Optional.empty());
        }

        Row at(Station station) {
            return new Row(account, system, Optional.of(station.id()), clusterId, action, old, fresh, outcome, error);
        }

        Row of(Cluster cluster) {
            return new Row(account, system, station, Optional.of(cluster.id()), action, old, fresh, outcome, error);
        }

        Row configs(Optional<String> before, Optional<String> after) {
            return new Row(account, system, station, clusterId, action, before, after, outcome, error);
        }

        Row failed(String reason) {
            return new Row(
                    account,
                    system,
                    station,
                    clusterId,
                    action,
                    old,
                    fresh,
                    StorageAuditOutcome.FAILED,
                    Optional.of(reason));
        }

        long insert() {
            return storageBackendAuditRepo.insert(new StorageBackendAuditRepository.NewEntry(
                    account, Optional.empty(), system, station, clusterId, action, old, fresh, outcome, error));
        }
    }

    @Test
    void insertReturnsGeneratedId() {
        long id = Row.by(account, StorageAuditAction.CREATED)
                .at(stationA)
                .configs(Optional.empty(), Optional.of("{\"kind\":\"LOCAL\"}"))
                .insert();
        assertTrue(id > 0);
    }

    @Test
    void insertWithSystemActorAndError() {
        long id = Row.bySystem("scheduler", StorageAuditAction.PROBE_FAILED)
                .configs(Optional.of("{\"kind\":\"S3\"}"), Optional.empty())
                .failed("connection refused")
                .insert();
        assertTrue(id > 0);
    }

    @Test
    void findRecentMatchingReturnsMostRecentMatch() {
        Instant base = Instant.now();
        long first = Row.by(account, StorageAuditAction.PROBE_OK).at(stationA).insert();
        long second = Row.by(account, StorageAuditAction.PROBE_OK).at(stationA).insert();

        var match = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.of(stationA.id()),
                Optional.empty(),
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
        Row.bySystem("scheduler", StorageAuditAction.MIGRATION_STARTED)
                .at(stationB)
                .insert();

        var future = storageBackendAuditRepo.findRecentMatching(
                Optional.empty(),
                Optional.of("scheduler"),
                Optional.of(stationB.id()),
                Optional.empty(),
                StorageAuditAction.MIGRATION_STARTED,
                StorageAuditOutcome.OK,
                Instant.now().plus(1, ChronoUnit.DAYS));
        assertTrue(future.isEmpty());
    }

    @Test
    void findRecentMatchingDistinguishesActors() {
        Row.by(account, StorageAuditAction.UPDATED)
                .configs(Optional.of("{\"a\":1}"), Optional.of("{\"a\":2}"))
                .insert();

        var wrongActor = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id() + 999),
                Optional.empty(),
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
                Optional.empty(),
                StorageAuditAction.UPDATED,
                StorageAuditOutcome.OK,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        assertTrue(rightActor.isPresent());
        assertTrue(rightActor.get().oldConfig().orElse("").contains("\"a\""));
        assertTrue(rightActor.get().newConfig().orElse("").contains("\"a\""));
    }

    /** An association's probe is its own and not the instance's, whoever probed both. */
    @Test
    void findRecentMatchingTellsAnAssociationFromTheInstance() {
        Row.by(account, StorageAuditAction.PROBE_FAILED)
                .of(cluster)
                .failed("refused")
                .insert();

        var forCluster = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.of(cluster.id()),
                StorageAuditAction.PROBE_FAILED,
                StorageAuditOutcome.FAILED,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        assertEquals(Optional.of(cluster.id()), forCluster.orElseThrow().clusterId());

        var forInstance = storageBackendAuditRepo.findRecentMatching(
                Optional.of(account.id()),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                StorageAuditAction.PROBE_FAILED,
                StorageAuditOutcome.FAILED,
                Instant.now().minus(1, ChronoUnit.MINUTES));
        assertTrue(forInstance.isEmpty());
    }

    @Test
    void findAllWithoutFiltersReturnsRowsDescending() {
        Row.by(account, StorageAuditAction.DELETED)
                .at(stationA)
                .configs(Optional.of("{\"kind\":\"S3\"}"), Optional.empty())
                .insert();

        var rows = storageBackendAuditRepo.findAll(Optional.empty(), Optional.empty(), 50);
        assertFalse(rows.isEmpty());
        for (int i = 1; i < rows.size(); i++) {
            assertFalse(rows.get(i - 1).ts().isBefore(rows.get(i).ts()));
        }
    }

    @Test
    void findAllWithBeforeFiltersOlder() {
        long id = Row.bySystem("scheduler", StorageAuditAction.INSTANCE_DEFAULT_UPDATED)
                .configs(Optional.empty(), Optional.of("{\"k\":\"v\"}"))
                .insert();

        var newer = storageBackendAuditRepo.findAll(
                Optional.of(Instant.now().plus(1, ChronoUnit.DAYS)), Optional.empty(), 10);
        assertTrue(newer.stream().anyMatch(r -> r.id() == id));

        var older = storageBackendAuditRepo.findAll(
                Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)), Optional.empty(), 10);
        assertTrue(older.stream().noneMatch(r -> r.id() == id));
    }

    @Test
    void findByStationOnlyReturnsThatStationsRows() {
        long aId = Row.by(account, StorageAuditAction.REJECTED)
                .at(stationA)
                .failed("non-empty backend")
                .insert();
        long bId = Row.by(account, StorageAuditAction.REJECTED)
                .at(stationB)
                .failed("non-empty backend")
                .insert();

        var rowsA = storageBackendAuditRepo.findByStation(stationA.id(), Optional.empty(), 50);
        assertTrue(rowsA.stream().anyMatch(r -> r.id() == aId));
        assertTrue(rowsA.stream().noneMatch(r -> r.id() == bId));
        assertTrue(rowsA.stream().allMatch(r -> r.stationId().equals(Optional.of(stationA.id()))));

        var rowsB = storageBackendAuditRepo.findByStation(stationB.id(), Optional.empty(), 50);
        assertTrue(rowsB.stream().anyMatch(r -> r.id() == bId));
        assertTrue(rowsB.stream().noneMatch(r -> r.id() == aId));
    }

    /**
     * An association's history holds its own rows and the moves it made for its stations, which the station's
     * history holds as well, and nothing of a station the association did not decide about.
     */
    @Test
    void findByClusterHoldsItsOwnRowsAndTheMovesItMade() {
        long own =
                Row.by(account, StorageAuditAction.POLICY_CHANGED).of(cluster).insert();
        long moved = Row.by(account, StorageAuditAction.MIGRATION_COMPLETED)
                .at(stationA)
                .of(cluster)
                .insert();
        long stationOnly = Row.by(account, StorageAuditAction.MIGRATION_COMPLETED)
                .at(stationA)
                .insert();

        var rows = storageBackendAuditRepo.findByCluster(cluster.id(), Optional.empty(), 50);
        assertTrue(rows.stream().anyMatch(r -> r.id() == own));
        assertTrue(rows.stream().anyMatch(r -> r.id() == moved));
        assertTrue(rows.stream().noneMatch(r -> r.id() == stationOnly));
        assertTrue(storageBackendAuditRepo.findByStation(stationA.id(), Optional.empty(), 50).stream()
                .anyMatch(r -> r.id() == moved));
        assertTrue(storageBackendAuditRepo
                .findByCluster(cluster.id(), Optional.of(Instant.now().minus(1, ChronoUnit.DAYS)), 50)
                .isEmpty());
    }

    @Test
    void newEntryRecordExposesEveryField() {
        var entry = new StorageBackendAuditRepository.NewEntry(
                Optional.of(7),
                Optional.of(11),
                Optional.of("boot"),
                Optional.of(13),
                Optional.of(17),
                StorageAuditAction.MIGRATION_COMPLETED,
                Optional.of("{}"),
                Optional.of("{\"x\":1}"),
                StorageAuditOutcome.OK,
                Optional.of("hint"));
        assertEquals(Optional.of(7), entry.actorAccountId());
        assertEquals(Optional.of(11), entry.actorMemberId());
        assertEquals(Optional.of("boot"), entry.systemActor());
        assertEquals(Optional.of(13), entry.stationId());
        assertEquals(Optional.of(17), entry.clusterId());
        assertEquals(StorageAuditAction.MIGRATION_COMPLETED, entry.action());
        assertEquals(Optional.of("{}"), entry.oldConfig());
        assertEquals(Optional.of("{\"x\":1}"), entry.newConfig());
        assertEquals(StorageAuditOutcome.OK, entry.outcome());
        assertEquals(Optional.of("hint"), entry.error());
    }
}
