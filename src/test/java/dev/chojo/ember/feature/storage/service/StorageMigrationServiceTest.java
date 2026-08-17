/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.HealthStatus;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests for the per-station backend migration. Two on-disk roots stand in for the
 * instance default and a station override - both LOCAL - which exercises the byte copy, the
 * SHA-256 skip on retry, the sample-verify, the override-row flip and the lock semantics
 * without needing a real remote target.
 */
class StorageMigrationServiceTest extends RepositoryTestBase {

    private static StationStorageConfigRepository storageConfigRepo;
    private static CredentialCipher cipher;

    private Path sourceRoot;
    private Path targetRoot;
    private LocalStorageBackend sourceBackend;
    private LocalStorageBackend targetBackend;
    private SwappableFactory factory;
    private StorageBackendResolver resolver;
    private StorageService storageService;
    private MigrationLockRegistry locks;
    private StorageMigrationService migrationService;
    private final List<Station> createdStations = new ArrayList<>();

    @BeforeAll
    static void initRepos() {
        storageConfigRepo = new StationStorageConfigRepository();
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    }

    private static StationStorageBackendConfig targetConfig() {
        return new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "eu-central-1",
                "station-bucket",
                true,
                Optional.empty(),
                "/",
                cipher.encrypt("{\"accessKey\":\"ak\",\"secretKey\":\"sk\"}"));
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] read(StorageBackend backend, String fullKey) {
        try (var stream = backend.read(fullKey).orElseThrow()) {
            return stream.body().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() throws Exception {
        sourceRoot = Files.createTempDirectory("station-migration-source");
        targetRoot = Files.createTempDirectory("station-migration-target");
        sourceBackend = new LocalStorageBackend(sourceRoot);
        targetBackend = new LocalStorageBackend(targetRoot);

        Storage storage = new Storage();
        setField(Storage.class, storage, "backend", new StorageBackendSettings());

        factory = new SwappableFactory(storage, sourceBackend, cipher);
        factory.stationTarget = targetBackend;
        factory.instanceBackend = sourceBackend;
        resolver = new StorageBackendResolver(factory, storageConfigRepo);
        storageService = new StorageService(resolver, sourceBackend);
        locks = new MigrationLockRegistry();
        migrationService = new StorageMigrationService(stationRepo, storageConfigRepo, factory, resolver, locks);
    }

    @AfterEach
    void cleanupStations() {
        for (Station station : createdStations) {
            storageConfigRepo.delete(station.id());
            stationRepo.delete(station.id());
        }
        createdStations.clear();
    }

    private Station newStation(String name) {
        Station station = stationRepo.create(name);
        createdStations.add(station);
        return station;
    }

    private String storeOnSource(Station station, String key, byte[] payload) {
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(scope, StorageCategory.PAGE_FILES, key, payload, "text/plain");
        return scope.prefix() + "/" + StorageCategory.PAGE_FILES.prefix() + "/" + key;
    }

    /**
     * The happy path: every station-scoped key moves onto the override backend, the override row
     * is written, and the source copies are deleted afterwards.
     */
    @Test
    void migrationCopiesStationBytesFlipsTheRowAndDeletesTheSource() {
        Station station = newStation("Station Migration Happy");
        byte[] payload = "page-file-payload".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(1, result.totalKeys());
        assertEquals(1, result.copied());
        assertEquals(0, result.skipped());
        assertEquals(1, result.deleted());
        assertEquals(payload.length, result.copiedBytes());
        assertArrayEquals(payload, read(targetBackend, fullKey));
        assertFalse(sourceBackend.exists(fullKey), "source bytes must be removed after a successful migration");
        assertTrue(storageConfigRepo.findOne(station.id()).isPresent(), "override row must be written");
        assertFalse(locks.isLocked(station.id()), "the station lock must be released");
    }

    /**
     * Idempotent re-run: a key already sitting on the target with matching content is skipped
     * instead of re-copied. The pre-placed copy carries no SHA in its sidecar, so the comparison
     * falls back to hashing the bytes on both sides.
     */
    @Test
    void keysAlreadyOnTheTargetWithMatchingContentAreSkipped() {
        Station station = newStation("Station Migration Skip");
        byte[] payload = "already-there".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);
        targetBackend.store(
                fullKey, new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(1, result.totalKeys());
        assertEquals(0, result.copied(), "matching keys must not be re-copied");
        assertEquals(1, result.skipped());
        assertEquals(0L, result.copiedBytes());
        assertEquals(1, result.deleted(), "the source copy is still cleaned up");
        assertArrayEquals(payload, read(targetBackend, fullKey));
    }

    /**
     * A key present on the target but holding different bytes is re-copied rather than trusted.
     */
    @Test
    void keysOnTheTargetWithDifferingContentAreOverwritten() {
        Station station = newStation("Station Migration Overwrite");
        byte[] payload = "authoritative".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);
        byte[] stale = "stale-copy".getBytes(StandardCharsets.UTF_8);
        targetBackend.store(fullKey, new ByteArrayInputStream(stale), stale.length, ObjectMetadata.of("text/plain"));

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(1, result.copied());
        assertEquals(0, result.skipped());
        assertArrayEquals(payload, read(targetBackend, fullKey));
    }

    /**
     * Bytes spread over several movable categories all travel, and each category's keys are
     * cleaned off the source.
     */
    @Test
    void everyMovableStationCategoryIsMigrated() {
        Station station = newStation("Station Migration Categories");
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(
                scope, StorageCategory.PAGE_FILES, "a.txt", "a".getBytes(StandardCharsets.UTF_8), "text/plain");
        storageService.store(
                scope, StorageCategory.KB_FILES, "b.txt", "b".getBytes(StandardCharsets.UTF_8), "text/plain");
        storageService.store(
                scope, StorageCategory.BOARD_ATTACHMENTS, "c.txt", "c".getBytes(StandardCharsets.UTF_8), "text/plain");

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(3, result.totalKeys());
        assertEquals(3, result.copied());
        assertEquals(3, result.deleted());
        assertTrue(targetBackend
                .listByPrefix(scope.prefix())
                .containsAll(List.of(
                        scope.prefix() + "/" + StorageCategory.PAGE_FILES.prefix() + "/a.txt",
                        scope.prefix() + "/" + StorageCategory.KB_FILES.prefix() + "/b.txt",
                        scope.prefix() + "/" + StorageCategory.BOARD_ATTACHMENTS.prefix() + "/c.txt")));
    }

    /**
     * A station with nothing stored migrates cleanly and simply flips the override row.
     */
    @Test
    void migratingAnEmptyStationOnlyFlipsTheRow() {
        Station station = newStation("Station Migration Empty");

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(0, result.totalKeys());
        assertEquals(0, result.deleted());
        assertTrue(storageConfigRepo.findOne(station.id()).isPresent());
    }

    /**
     * Only one migration per station at a time; the second caller is refused without touching
     * any bytes.
     */
    @Test
    void aSecondMigrationForTheSameStationIsRefused() {
        Station station = newStation("Station Migration Locked");
        assertTrue(locks.tryAcquire(station.id()));
        try {
            var error = assertThrows(
                    MigrationException.class, () -> migrationService.migrate(station.id(), targetConfig()));
            assertTrue(error.getMessage().contains("already in flight"));
        } finally {
            locks.release(station.id());
        }
    }

    /**
     * An unknown station id fails loudly and - importantly - releases the lock it took, so a
     * retry after the caller fixes the id is not blocked forever.
     */
    @Test
    void anUnresolvableStationFailsAndReleasesTheLock() {
        int unknownId = 987654321;

        assertThrows(MigrationException.class, () -> migrationService.migrate(unknownId, targetConfig()));

        assertFalse(locks.isLocked(unknownId), "the lock must be released before the exception propagates");
    }

    /**
     * A target that fails its health probe aborts before a single byte is copied, and leaves the
     * station on its previous backend.
     */
    @Test
    void anUnhealthyTargetAbortsBeforeCopying() {
        Station station = newStation("Station Migration Bad Probe");
        String fullKey = storeOnSource(station, "doc.txt", "keep-me".getBytes(StandardCharsets.UTF_8));
        factory.stationTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public HealthStatus probe() {
                return HealthStatus.unhealthy("no route to host");
            }
        };

        var error =
                assertThrows(MigrationException.class, () -> migrationService.migrate(station.id(), targetConfig()));

        assertTrue(error.getMessage().contains("no route to host"));
        assertTrue(sourceBackend.exists(fullKey), "source bytes must be untouched after a failed probe");
        assertTrue(storageConfigRepo.findOne(station.id()).isEmpty(), "the override row must not be written");
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * Sample verification is the last line of defence: a target that accepts writes but cannot
     * read them back aborts the migration instead of deleting the source.
     */
    @Test
    void sampleVerificationFailureAbortsTheMigration() {
        Station station = newStation("Station Migration Bad Verify");
        String fullKey = storeOnSource(station, "doc.txt", "verify-me".getBytes(StandardCharsets.UTF_8));
        factory.stationTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public boolean exists(String key) {
                return false;
            }
        };

        var error =
                assertThrows(MigrationException.class, () -> migrationService.migrate(station.id(), targetConfig()));

        assertTrue(error.getMessage().contains("Sample verification failed"));
        assertTrue(sourceBackend.exists(fullKey), "the source must survive a failed verification");
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * The delete sweep is best-effort: a source that refuses to drop migrated keys is logged and
     * the migration still reports success, since the bytes are already safe on the target.
     */
    @Test
    void sourceDeleteFailuresDoNotFailTheMigration() {
        Station station = newStation("Station Migration Delete Failure");
        byte[] payload = "stubborn".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);
        factory.instanceBackend = new LocalStorageBackend(sourceRoot) {
            @Override
            public void delete(String key) {
                throw new StorageException("read-only filesystem");
            }
        };

        var result = migrationService.migrate(station.id(), targetConfig());

        assertEquals(1, result.copied());
        assertEquals(0, result.deleted(), "keys that could not be deleted are not counted");
        assertArrayEquals(payload, read(targetBackend, fullKey));
    }

    /**
     * Moving back to the instance default is a no-op for a station that never had an override.
     */
    @Test
    void migratingBackWithoutAnOverrideIsANoOp() {
        Station station = newStation("Station Migration No Override");

        var result = migrationService.migrateToInstanceDefault(station.id());

        assertEquals(0, result.totalKeys());
        assertEquals(0, result.copied());
        assertEquals(0, result.deleted());
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * Round trip: migrate a station out to its own backend, then back to the instance default.
     * The bytes land back on the instance default and the override row is dropped.
     */
    @Test
    void migratingBackMovesBytesHomeAndDropsTheOverrideRow() {
        Station station = newStation("Station Migration Round Trip");
        byte[] payload = "round-trip".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);
        migrationService.migrate(station.id(), targetConfig());
        assertFalse(sourceBackend.exists(fullKey));

        var result = migrationService.migrateToInstanceDefault(station.id());

        assertEquals(1, result.totalKeys());
        assertEquals(1, result.copied());
        assertEquals(1, result.deleted());
        assertArrayEquals(payload, read(sourceBackend, fullKey));
        assertFalse(targetBackend.exists(fullKey), "the override backend is cleaned up");
        assertTrue(storageConfigRepo.findOne(station.id()).isEmpty(), "the override row must be dropped");
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * Re-running the way home skips keys the instance default already holds with the same bytes.
     */
    @Test
    void migratingBackSkipsKeysTheInstanceDefaultAlreadyHolds() {
        Station station = newStation("Station Migration Back Skip");
        byte[] payload = "already-home".getBytes(StandardCharsets.UTF_8);
        String fullKey = storeOnSource(station, "doc.txt", payload);
        migrationService.migrate(station.id(), targetConfig());
        sourceBackend.store(
                fullKey, new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        var result = migrationService.migrateToInstanceDefault(station.id());

        assertEquals(1, result.totalKeys());
        assertEquals(0, result.copied());
        assertEquals(1, result.skipped());
    }

    /**
     * The way home takes the same per-station lock, so it is refused while another migration for
     * that station is in flight.
     */
    @Test
    void migratingBackIsRefusedWhileTheStationIsLocked() {
        Station station = newStation("Station Migration Back Locked");
        assertTrue(locks.tryAcquire(station.id()));
        try {
            assertThrows(MigrationException.class, () -> migrationService.migrateToInstanceDefault(station.id()));
        } finally {
            locks.release(station.id());
        }
    }

    /**
     * An unknown station id on the way home behaves like the outbound direction: loud failure,
     * lock released.
     */
    @Test
    void migratingBackAnUnresolvableStationFailsAndReleasesTheLock() {
        int unknownId = 987654322;

        assertThrows(MigrationException.class, () -> migrationService.migrateToInstanceDefault(unknownId));

        assertFalse(locks.isLocked(unknownId));
    }

    /**
     * An unhealthy instance default aborts the way home; the station stays on its override so no
     * bytes are stranded.
     */
    @Test
    void migratingBackAbortsWhenTheInstanceDefaultIsUnhealthy() {
        Station station = newStation("Station Migration Back Bad Probe");
        storeOnSource(station, "doc.txt", "stay".getBytes(StandardCharsets.UTF_8));
        migrationService.migrate(station.id(), targetConfig());
        factory.instanceBackend = new LocalStorageBackend(sourceRoot) {
            @Override
            public HealthStatus probe() {
                return HealthStatus.unhealthy("disk full");
            }
        };

        var error =
                assertThrows(MigrationException.class, () -> migrationService.migrateToInstanceDefault(station.id()));

        assertTrue(error.getMessage().contains("disk full"));
        assertTrue(storageConfigRepo.findOne(station.id()).isPresent(), "the override row must survive");
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * A failure raised while walking the source is wrapped into the migration's own exception
     * type so the route layer only has to catch one thing.
     */
    @Test
    void unexpectedBackendFailuresAreWrappedAsMigrationExceptions() {
        Station station = newStation("Station Migration Wrapped");
        storeOnSource(station, "doc.txt", "boom".getBytes(StandardCharsets.UTF_8));
        factory.stationTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public void store(String key, InputStream body, long contentLength, ObjectMetadata metadata) {
                throw new StorageException("target went away");
            }
        };

        var error =
                assertThrows(MigrationException.class, () -> migrationService.migrate(station.id(), targetConfig()));

        assertTrue(error.getMessage().contains("Migration failed"));
        assertInstanceOf(StorageException.class, error.getCause());
        assertFalse(locks.isLocked(station.id()));
    }

    /**
     * Factory stand-in that lets a test point the station-override backend and the instance
     * default at arbitrary local roots (or at deliberately misbehaving wrappers) without needing
     * a real S3 / SMB / SFTP endpoint behind the encrypted config row.
     */
    private static final class SwappableFactory extends StorageBackendFactory {
        private StorageBackend stationTarget;
        private StorageBackend instanceBackend;

        private SwappableFactory(Storage storage, LocalStorageBackend local, CredentialCipher cipher) {
            super(storage, local, cipher);
        }

        @Override
        public StorageBackend buildForStation(StationStorageBackendConfig config) {
            return stationTarget;
        }

        @Override
        public synchronized StorageBackend instanceDefault() {
            return instanceBackend;
        }
    }
}
