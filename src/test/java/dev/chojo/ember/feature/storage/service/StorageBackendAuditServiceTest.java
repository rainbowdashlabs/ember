/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditEntry;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.RedactedStationConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
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
 * Behavioural tests for the storage backend audit trail. Every assertion reads the row back
 * through the repository, so the redaction contract (no cipher material in the table) and the
 * probe dedupe window are checked against what actually landed in the database.
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

    private static StationStorageBackendConfig s3Config(String bucket) {
        return new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "eu-central-1",
                bucket,
                true,
                Optional.of("AES256"),
                "/",
                cipher.encrypt("{\"accessKey\":\"ak\",\"secretKey\":\"sk\"}"));
    }

    private static StationStorageBackendConfig smbConfig() {
        return new StationStorageBackendConfig.SmbVariant(
                "smb.example.invalid",
                445,
                "share",
                "WORKGROUP",
                "/base",
                true,
                false,
                cipher.encrypt("{\"username\":\"u\",\"password\":\"p\"}"));
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
     * A CREATED event carries only the new config, and the persisted snapshot is redacted —
     * the plaintext secret key must never reach the table.
     */
    @Test
    void configCreationRecordsRedactedNewSnapshotOnly() {
        service.recordConfigChange(
                StorageBackendAuditService.Actor.human(account.id(), null),
                station.id(),
                StorageAuditAction.CREATED,
                null,
                s3Config("created-bucket"));

        StorageAuditEntry row = latestFor(StorageAuditAction.CREATED);
        assertEquals(Optional.of(account.id()), row.actorAccountId());
        assertEquals(Optional.empty(), row.actorMemberId());
        assertEquals(StorageAuditOutcome.OK, row.outcome());
        assertTrue(row.oldConfig().isEmpty(), "CREATED must not carry an old snapshot");
        String newConfig = row.newConfig().orElseThrow();
        assertTrue(newConfig.contains("created-bucket"));
        assertTrue(newConfig.contains(RedactedStationConfig.REDACTED_MARKER));
        assertFalse(newConfig.contains("sk"), "secret key must not reach the audit table");
    }

    /**
     * An UPDATED event carries both snapshots so a reader can diff the non-secret shape, and a
     * DELETED event carries only the old one.
     */
    @Test
    void updateCarriesBothSnapshotsAndDeleteOnlyTheOld() {
        service.recordConfigChange(
                StorageBackendAuditService.Actor.human(account.id(), null),
                station.id(),
                StorageAuditAction.UPDATED,
                s3Config("old-bucket"),
                smbConfig());
        StorageAuditEntry updated = latestFor(StorageAuditAction.UPDATED);
        assertTrue(updated.oldConfig().orElseThrow().contains("old-bucket"));
        assertTrue(updated.newConfig().orElseThrow().contains("smb.example.invalid"));

        service.recordConfigChange(
                StorageBackendAuditService.Actor.system("migration"),
                station.id(),
                StorageAuditAction.DELETED,
                smbConfig(),
                null);
        StorageAuditEntry deleted = latestFor(StorageAuditAction.DELETED);
        assertEquals(Optional.of("migration"), deleted.systemActor());
        assertTrue(deleted.oldConfig().isPresent());
        assertTrue(deleted.newConfig().isEmpty(), "DELETED must not carry a new snapshot");
    }

    /**
     * A rejected mutation lands with the FAILED outcome, the operator-facing reason, and the
     * attempted config in the new-config column.
     */
    @Test
    void rejectionCarriesReasonAndAttemptedConfig() {
        service.recordRejected(
                StorageBackendAuditService.Actor.human(account.id(), null),
                station.id(),
                Optional.of(s3Config("rejected-bucket")),
                "backend still holds bytes");

        StorageAuditEntry row = latestFor(StorageAuditAction.REJECTED);
        assertEquals(StorageAuditOutcome.FAILED, row.outcome());
        assertEquals(Optional.of("backend still holds bytes"), row.error());
        assertTrue(row.newConfig().orElseThrow().contains("rejected-bucket"));
        assertTrue(row.oldConfig().isEmpty());
    }

    /**
     * Rejections raised before any config was parsed carry no snapshot at all.
     */
    @Test
    void rejectionWithoutAttemptedConfigStoresNoSnapshot() {
        Station lonely = stationRepo.create("Rejection Without Config");
        try {
            service.recordRejected(
                    StorageBackendAuditService.Actor.system("route"),
                    lonely.id(),
                    Optional.empty(),
                    "credential encryption key not configured");

            StorageAuditEntry row = storageBackendAuditRepo
                    .findByStation(lonely.id(), Optional.empty(), 10)
                    .getFirst();
            assertEquals(StorageAuditAction.REJECTED, row.action());
            assertTrue(row.newConfig().isEmpty());
            assertTrue(row.oldConfig().isEmpty());
        } finally {
            stationRepo.delete(lonely.id());
        }
    }

    /**
     * The admin panel auto-refreshes; back-to-back probes from the same actor with the same
     * outcome collapse into a single row inside the dedupe window.
     */
    @Test
    void repeatedProbesInsideTheDedupeWindowCollapseToOneRow() {
        Station probed = stationRepo.create("Probe Dedupe Station");
        try {
            var actor = StorageBackendAuditService.Actor.human(account.id(), null);
            service.recordProbe(actor, probed.id(), StorageAuditOutcome.OK, null);
            service.recordProbe(actor, probed.id(), StorageAuditOutcome.OK, null);
            service.recordProbe(actor, probed.id(), StorageAuditOutcome.OK, null);

            var rows = storageBackendAuditRepo.findByStation(probed.id(), Optional.empty(), 50);
            assertEquals(1, rows.size(), "probes inside the dedupe window must collapse");
            assertEquals(StorageAuditAction.PROBE_OK, rows.getFirst().action());
        } finally {
            stationRepo.delete(probed.id());
        }
    }

    /**
     * The dedupe key includes the outcome: a probe that starts failing is recorded even though
     * a successful probe from the same actor is still inside the window.
     */
    @Test
    void probeWithADifferentOutcomeIsNotDeduped() {
        Station flipping = stationRepo.create("Probe Flip Station");
        try {
            var actor = StorageBackendAuditService.Actor.human(account.id(), null);
            service.recordProbe(actor, flipping.id(), StorageAuditOutcome.OK, null);
            service.recordProbe(actor, flipping.id(), StorageAuditOutcome.FAILED, "connection refused");

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
     * Migration lifecycle events derive their outcome from the action: only the FAILED action
     * writes a FAILED outcome, and the failure reason rides along.
     */
    @Test
    void migrationOutcomeFollowsTheAction() {
        Station migrating = stationRepo.create("Migration Lifecycle Station");
        try {
            var actor = StorageBackendAuditService.Actor.human(account.id(), null);
            service.recordMigration(
                    actor, migrating.id(), StorageAuditAction.MIGRATION_STARTED, null, s3Config("target"), null);
            service.recordMigration(
                    actor,
                    migrating.id(),
                    StorageAuditAction.MIGRATION_FAILED,
                    smbConfig(),
                    s3Config("target"),
                    "target probe failed");
            service.recordMigration(
                    actor, migrating.id(), StorageAuditAction.MIGRATION_COMPLETED, null, s3Config("target"), null);

            var rows = storageBackendAuditRepo.findByStation(migrating.id(), Optional.empty(), 50);
            assertEquals(3, rows.size());
            for (StorageAuditEntry row : rows) {
                StorageAuditOutcome expected = row.action() == StorageAuditAction.MIGRATION_FAILED
                        ? StorageAuditOutcome.FAILED
                        : StorageAuditOutcome.OK;
                assertEquals(expected, row.outcome(), "outcome for " + row.action());
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
     * Instance-level events are station-less and pass the caller's pre-redacted JSON straight
     * through, since the instance config shape is owned by the route layer.
     */
    @Test
    void instanceEventsAreStationLessAndPassCallerJsonThrough() {
        var actor = StorageBackendAuditService.Actor.system("admin-panel");
        service.recordInstanceConfigUpdate(actor, "{\"type\":\"LOCAL\"}", "{\"type\":\"S3\"}");
        service.recordInstanceMigration(
                actor,
                StorageAuditAction.INSTANCE_MIGRATION_FAILED,
                "{\"type\":\"LOCAL\"}",
                "{\"type\":\"S3\"}",
                "instance probe failed");
        service.recordInstanceMigration(
                actor,
                StorageAuditAction.INSTANCE_MIGRATION_COMPLETED,
                "{\"type\":\"LOCAL\"}",
                "{\"type\":\"S3\"}",
                null);

        var all = storageBackendAuditRepo.findAll(Optional.empty(), Optional.empty(), 200);
        var update = all.stream()
                .filter(row -> row.action() == StorageAuditAction.INSTANCE_DEFAULT_UPDATED)
                .findFirst()
                .orElseThrow();
        assertTrue(update.stationId().isEmpty(), "instance events carry no station");
        assertTrue(update.newConfig().orElseThrow().contains("S3"));
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
     * The actor factories fill exactly one of the account / system slots, and the member id is
     * carried only when the human actor is also a station member.
     */
    @Test
    void actorFactoriesPopulateExactlyOneAttributionSlot() {
        var human = StorageBackendAuditService.Actor.human(42, 7);
        assertEquals(Optional.of(42), human.accountId());
        assertEquals(Optional.of(7), human.memberId());
        assertTrue(human.systemActor().isEmpty());

        var humanWithoutMembership = StorageBackendAuditService.Actor.human(42, null);
        assertTrue(humanWithoutMembership.memberId().isEmpty());

        var system = StorageBackendAuditService.Actor.system("boot");
        assertTrue(system.accountId().isEmpty());
        assertTrue(system.memberId().isEmpty());
        assertEquals(Optional.of("boot"), system.systemActor());
    }
}
