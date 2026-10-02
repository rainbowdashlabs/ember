/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioural tests for the storage history. Every assertion reads the row back through the repository, so
 * the redaction contract (no cipher material in the table), the owner a row is filed under and the probe
 * dedupe window are checked against what actually landed in the database.
 */
class StorageBackendAuditServiceTest extends RepositoryTestBase {

    private static StorageBackendAuditService service;
    private static CredentialCipher cipher;
    private static Account account;
    private static Station station;

    @BeforeAll
    static void setup() {
        service = new StorageBackendAuditService(storageBackendAuditRepo);
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        account = accountRepo.create("storage-audit-service@test.example", "Aud", "Svc");
        accountRepo.setInstanceUserType(account.id(), InstanceUserType.ADMINISTRATOR);
        station = stationRepo.create("Storage Audit Service Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static BackendSummary s3(String bucket) {
        return BackendRedaction.summaryOf(new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "eu-central-1",
                bucket,
                true,
                Optional.of("AES256"),
                "/",
                cipher.encrypt("{\"accessKey\":\"ak\",\"secretKey\":\"sk\"}")));
    }

    private static BackendSummary smb() {
        return BackendRedaction.summaryOf(new StationStorageBackendConfig.SmbVariant(
                "smb.example.invalid",
                445,
                "share",
                "WORKGROUP",
                "/base",
                true,
                false,
                cipher.encrypt("{\"username\":\"u\",\"password\":\"p\"}")));
    }

    private static Actor human() {
        return Actor.human(account.id(), null);
    }

    private List<StorageAuditEntry> rowsForStation() {
        return storageBackendAuditRepo.findByStation(station.id(), Optional.empty(), 100);
    }

    private StorageAuditEntry latestFor(StorageAuditAction action) {
        return rowsForStation().stream()
                .filter(row -> row.action() == action)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no audit row recorded for " + action));
    }

    /**
     * A CREATED event carries only the new storage, redacted to a fingerprint: the secret key must never
     * reach the table.
     */
    @Test
    void configCreationRecordsRedactedNewSnapshotOnly() {
        service.recordConfigChange(
                human(), new Owner.Station(station.id()), StorageAuditAction.CREATED, null, s3("created-bucket"));

        StorageAuditEntry row = latestFor(StorageAuditAction.CREATED);
        assertEquals(Optional.of(account.id()), row.actorAccountId());
        assertEquals(Optional.empty(), row.actorMemberId());
        assertEquals(StorageAuditOutcome.OK, row.outcome());
        assertTrue(row.oldConfig().isEmpty(), "CREATED must not carry an old snapshot");
        String newConfig = row.newConfig().orElseThrow();
        assertTrue(newConfig.contains("created-bucket"));
        assertTrue(newConfig.contains("credentialFingerprint"));
        assertFalse(newConfig.contains("\"sk\""), "secret key must not reach the audit table");
    }

    /**
     * An UPDATED event carries both snapshots so a reader can diff the non-secret shape, and a DELETED event
     * carries only the old one.
     */
    @Test
    void updateCarriesBothSnapshotsAndDeleteOnlyTheOld() {
        service.recordConfigChange(
                human(), new Owner.Station(station.id()), StorageAuditAction.UPDATED, s3("old-bucket"), smb());
        StorageAuditEntry updated = latestFor(StorageAuditAction.UPDATED);
        assertTrue(updated.oldConfig().orElseThrow().contains("old-bucket"));
        assertTrue(updated.newConfig().orElseThrow().contains("smb.example.invalid"));

        service.recordConfigChange(
                Actor.system("migration"), new Owner.Station(station.id()), StorageAuditAction.DELETED, smb(), null);
        StorageAuditEntry deleted = latestFor(StorageAuditAction.DELETED);
        assertEquals(Optional.of("migration"), deleted.systemActor());
        assertTrue(deleted.oldConfig().isPresent());
        assertTrue(deleted.newConfig().isEmpty(), "DELETED must not carry a new snapshot");
    }

    /** A refused change lands with the FAILED outcome and the code and sentence the person was given. */
    @Test
    void rejectionCarriesTheRefusalItWasAnsweredWith() {
        Station lonely = stationRepo.create("Rejection Station");
        try {
            service.recordRejected(
                    human(), new Owner.Station(lonely.id()), StorageRefusal.STORAGE_KEYS_MISSING.raise());

            StorageAuditEntry row = storageBackendAuditRepo
                    .findByStation(lonely.id(), Optional.empty(), 10)
                    .getFirst();
            assertEquals(StorageAuditAction.REJECTED, row.action());
            assertEquals(StorageAuditOutcome.FAILED, row.outcome());
            assertEquals(
                    Optional.of(StorageRefusal.STORAGE_KEYS_MISSING.code() + ": "
                            + StorageRefusal.STORAGE_KEYS_MISSING.message()),
                    row.error());
            assertTrue(row.newConfig().isEmpty());
            assertTrue(row.oldConfig().isEmpty());
        } finally {
            stationRepo.delete(lonely.id());
        }
    }

    /**
     * A screen auto-refreshes; back-to-back probes from the same actor with the same outcome collapse into a
     * single row inside the dedupe window.
     */
    @Test
    void repeatedProbesInsideTheDedupeWindowCollapseToOneRow() {
        Station probed = stationRepo.create("Probe Dedupe Station");
        try {
            var owner = new Owner.Station(probed.id());
            service.recordProbe(human(), owner, StorageAuditOutcome.OK, null);
            service.recordProbe(human(), owner, StorageAuditOutcome.OK, null);
            service.recordProbe(human(), owner, StorageAuditOutcome.OK, null);

            var rows = storageBackendAuditRepo.findByStation(probed.id(), Optional.empty(), 50);
            assertEquals(1, rows.size(), "probes inside the dedupe window must collapse");
            assertEquals(StorageAuditAction.PROBE_OK, rows.getFirst().action());
        } finally {
            stationRepo.delete(probed.id());
        }
    }

    /**
     * The dedupe key includes the outcome: a probe that starts failing is recorded even though a successful
     * probe from the same actor is still inside the window.
     */
    @Test
    void probeWithADifferentOutcomeIsNotDeduped() {
        Station flipping = stationRepo.create("Probe Flip Station");
        try {
            var owner = new Owner.Station(flipping.id());
            service.recordProbe(human(), owner, StorageAuditOutcome.OK, null);
            service.recordProbe(human(), owner, StorageAuditOutcome.FAILED, "connection refused");

            var rows = storageBackendAuditRepo.findByStation(flipping.id(), Optional.empty(), 50);
            assertEquals(2, rows.size());
            var failed = rows.stream()
                    .filter(row -> row.action() == StorageAuditAction.PROBE_FAILED)
                    .findFirst()
                    .orElseThrow();
            assertEquals(StorageAuditOutcome.FAILED, failed.outcome());
            assertEquals(Optional.of("connection refused"), failed.error());
        } finally {
            stationRepo.delete(flipping.id());
        }
    }

    /**
     * Move steps derive their outcome from the action: only the FAILED action writes a FAILED outcome, and
     * the failure reason rides along.
     */
    @Test
    void migrationOutcomeFollowsTheAction() {
        Station migrating = stationRepo.create("Migration Lifecycle Station");
        try {
            var owner = new Owner.Station(migrating.id());
            service.recordMove(
                    human(), owner, migrating.id(), StorageAuditAction.MIGRATION_STARTED, null, s3("target"), null);
            service.recordMove(
                    human(),
                    owner,
                    migrating.id(),
                    StorageAuditAction.MIGRATION_FAILED,
                    smb(),
                    s3("target"),
                    "target probe failed");
            service.recordMove(
                    human(), owner, migrating.id(), StorageAuditAction.MIGRATION_COMPLETED, null, s3("target"), null);

            var rows = storageBackendAuditRepo.findByStation(migrating.id(), Optional.empty(), 50);
            assertEquals(3, rows.size());
            for (StorageAuditEntry row : rows) {
                StorageAuditOutcome expected = row.action() == StorageAuditAction.MIGRATION_FAILED
                        ? StorageAuditOutcome.FAILED
                        : StorageAuditOutcome.OK;
                assertEquals(expected, row.outcome(), "outcome for " + row.action());
                assertTrue(row.clusterId().isEmpty(), "a station's own move is its own history only");
            }
            var failed = rows.stream()
                    .filter(row -> row.action() == StorageAuditAction.MIGRATION_FAILED)
                    .findFirst()
                    .orElseThrow();
            assertEquals(Optional.of("target probe failed"), failed.error());
            assertTrue(failed.oldConfig().orElseThrow().contains("smb.example.invalid"));
        } finally {
            stationRepo.delete(migrating.id());
        }
    }

    /**
     * What an association does to its own storage is its history alone, and a move it makes for one of its
     * stations is both histories.
     */
    @Test
    void anAssociationsRowsAreFiledUnderItAndItsMovesUnderBoth() {
        Cluster cluster = clusterService.create("Audit Verband Service", null);
        Station moved = stationRepo.create("Audit Verband Station");
        try {
            var owner = new Owner.Association(cluster.id());
            service.recordConfigChange(human(), owner, StorageAuditAction.CREATED, null, smb());
            service.recordPolicyChange(
                    human(),
                    cluster.id(),
                    new StorageBackendAuditService.Policy(ClusterBackendReach.NONE, false),
                    new StorageBackendAuditService.Policy(ClusterBackendReach.EVERY_STATION, true));
            service.recordMove(
                    Actor.system("association-join"),
                    owner,
                    moved.id(),
                    StorageAuditAction.MIGRATION_COMPLETED,
                    null,
                    smb(),
                    null);

            var history = storageBackendAuditRepo.findByCluster(cluster.id(), Optional.empty(), 50);
            assertEquals(3, history.size());
            var policy = history.stream()
                    .filter(row -> row.action() == StorageAuditAction.POLICY_CHANGED)
                    .findFirst()
                    .orElseThrow();
            assertTrue(policy.stationId().isEmpty());
            assertTrue(policy.newConfig().orElseThrow().contains("EVERY_STATION"));
            assertTrue(policy.oldConfig().orElseThrow().contains("NONE"));

            var stationHistory = storageBackendAuditRepo.findByStation(moved.id(), Optional.empty(), 50);
            assertEquals(1, stationHistory.size());
            assertEquals(Optional.of(cluster.id()), stationHistory.getFirst().clusterId());
            assertEquals(
                    Optional.of("association-join"), stationHistory.getFirst().systemActor());
        } finally {
            stationRepo.delete(moved.id());
            clusterService.delete(cluster.id());
        }
    }

    /** Instance events belong to nobody's history but the instance's and carry its storage redacted. */
    @Test
    void instanceEventsAreStationLess() {
        var actor = Actor.system("admin-panel");
        var local = new BackendSummary.LocalSummary("data");
        service.recordConfigChange(
                actor, new Owner.Instance(), StorageAuditAction.INSTANCE_DEFAULT_UPDATED, local, s3("instance"));
        service.recordInstanceMigration(
                actor, StorageAuditAction.INSTANCE_MIGRATION_FAILED, local, s3("instance"), "instance probe failed");
        service.recordInstanceMigration(
                actor, StorageAuditAction.INSTANCE_MIGRATION_COMPLETED, local, s3("instance"), null);

        var all = storageBackendAuditRepo.findAll(Optional.empty(), Optional.empty(), 200);
        var update = all.stream()
                .filter(row -> row.action() == StorageAuditAction.INSTANCE_DEFAULT_UPDATED)
                .findFirst()
                .orElseThrow();
        assertTrue(update.stationId().isEmpty(), "instance events carry no station");
        assertTrue(update.clusterId().isEmpty(), "nor an association");
        assertTrue(update.oldConfig().orElseThrow().contains("LOCAL"));
        assertTrue(update.newConfig().orElseThrow().contains("instance"));
        assertEquals(StorageAuditOutcome.OK, update.outcome());

        var failed = all.stream()
                .filter(row -> row.action() == StorageAuditAction.INSTANCE_MIGRATION_FAILED)
                .findFirst()
                .orElseThrow();
        assertEquals(StorageAuditOutcome.FAILED, failed.outcome());
        assertEquals(Optional.of("instance probe failed"), failed.error());

        var completed = all.stream()
                .filter(row -> row.action() == StorageAuditAction.INSTANCE_MIGRATION_COMPLETED)
                .findFirst()
                .orElseThrow();
        assertEquals(StorageAuditOutcome.OK, completed.outcome());
        assertTrue(completed.error().isEmpty());
    }

    /**
     * The actor factories fill exactly one of the account / system slots, and the member id is carried only
     * when the human actor is also a station member.
     */
    @Test
    void actorFactoriesPopulateExactlyOneAttributionSlot() {
        var human = Actor.human(42, 7);
        assertEquals(Optional.of(42), human.accountId());
        assertEquals(Optional.of(7), human.memberId());
        assertTrue(human.systemActor().isEmpty());

        var humanWithoutMembership = Actor.human(42, null);
        assertTrue(humanWithoutMembership.memberId().isEmpty());

        var system = Actor.system("boot");
        assertTrue(system.accountId().isEmpty());
        assertTrue(system.memberId().isEmpty());
        assertEquals(Optional.of("boot"), system.systemActor());
    }
}
