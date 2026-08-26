/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.conf.file.elements.StorageBackendSettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendFactory;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.backend.StorageException;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests for the instance-wide backend migration. Two on-disk roots stand in for two
 * different backends - both LOCAL - which is enough to exercise the byte copy, the SHA-256
 * skip on retry, the sample-verify, and the lock semantics from §19.A without spinning up a
 * real remote target.
 */
class InstanceStorageMigrationServiceTest extends RepositoryTestBase {

    private static StationStorageConfigRepository storageConfigRepo;

    private Path sourceRoot;
    private Path targetRoot;
    private LocalStorageBackend sourceBackend;
    private LocalStorageBackend targetBackend;
    private SwappableFactory factory;
    private StorageService storageService;
    private InstanceStorageMigrationService migrationService;
    private MigrationLockRegistry locks;
    private InstanceStorageReadOnlyState readOnly;

    @BeforeAll
    static void initRepos() {
        storageConfigRepo = new StationStorageConfigRepository();
    }

    @BeforeEach
    void setup() throws Exception {
        sourceRoot = Files.createTempDirectory("instance-migration-source");
        targetRoot = Files.createTempDirectory("instance-migration-target");
        sourceBackend = new LocalStorageBackend(sourceRoot);
        targetBackend = new LocalStorageBackend(targetRoot);
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));

        Storage storage = new Storage();
        setField(Storage.class, storage, "backend", new StorageBackendSettings());
        setField(StorageBackendSettings.class, storage.backend(), "type", StorageBackendType.LOCAL);
        setField(StorageBackendSettings.LocalSettings.class, storage.backend().local(), "root", sourceRoot.toString());

        factory = new SwappableFactory(storage, sourceBackend, cipher);
        var resolver = new StorageBackendResolver(factory, storageConfigRepo, new ClusterStationStorageRepository());
        storageService = new StorageService(resolver, sourceBackend);

        locks = new MigrationLockRegistry();
        readOnly = new InstanceStorageReadOnlyState();
        migrationService = new InstanceStorageMigrationService(
                stationRepo,
                accountRepo,
                storageConfigRepo,
                new ClusterStationStorageRepository(),
                factory,
                locks,
                readOnly);
    }

    /**
     * End-to-end migration: stations without an override have their bytes copied onto the
     * supplied target. Sample-verify checks each migrated key is readable on the target. The
     * read-only flag is held during the copy and cleared after commit.
     */
    @Test
    void localToLocalMigrationCopiesEveryStationsBytes() {
        Station alpha = stationRepo.create("Alpha");
        Station beta = stationRepo.create("Beta");
        byte[] alphaBytes = "alpha-payload".getBytes(StandardCharsets.UTF_8);
        byte[] betaBytes = "beta-payload".getBytes(StandardCharsets.UTF_8);
        storageService.store(
                new StorageScope.Station(alpha.id(), alpha.uid()),
                StorageCategory.MEDIA_FILES,
                "alpha.txt",
                alphaBytes,
                "text/plain");
        storageService.store(
                new StorageScope.Station(beta.id(), beta.uid()),
                StorageCategory.MEDIA_FILES,
                "beta.txt",
                betaBytes,
                "text/plain");

        StorageBackendSettings targetSettings = newLocalSettings(targetRoot.toString());

        var prepared = migrationService.prepare(targetSettings);
        assertTrue(readOnly.isLocked(), "read-only flag must be held during the copy");
        assertTrue(locks.isInstanceLocked(), "instance lock must be held during the copy");

        var result = migrationService.commit(prepared, false);
        assertFalse(readOnly.isLocked(), "read-only flag must clear after commit");
        assertFalse(locks.isInstanceLocked(), "instance lock must release after commit");

        assertEquals(2, result.totalKeys());
        assertEquals(2, result.copied());
        assertEquals(0, result.skipped());
        assertEquals(2, result.deleted());

        assertArrayEquals(alphaBytes, readKey(targetBackend, alpha.uid().toString(), "alpha.txt"));
        assertArrayEquals(betaBytes, readKey(targetBackend, beta.uid().toString(), "beta.txt"));
    }

    /**
     * Idempotent retry: a key already present on the target with a matching SHA is skipped on
     * the second run. The copied counter drops; the skipped counter rises.
     */
    @Test
    void rerunSkipsKeysAlreadyOnTargetWithMatchingHash() {
        Station station = stationRepo.create("Gamma");
        byte[] payload = "gamma-payload".getBytes(StandardCharsets.UTF_8);
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(scope, StorageCategory.MEDIA_FILES, "doc.txt", payload, "text/plain");

        StorageBackendSettings targetSettings = newLocalSettings(targetRoot.toString());
        var firstRun = migrationService.commit(migrationService.prepare(targetSettings), true);
        assertEquals(1, firstRun.copied());
        assertEquals(0, firstRun.skipped());

        var secondRun = migrationService.commit(migrationService.prepare(targetSettings), true);
        assertEquals(0, secondRun.copied(), "second run should not re-copy");
        assertEquals(1, secondRun.skipped(), "second run should skip the matching key");
    }

    /**
     * §19.A mutual-exclusion rule: per-station and instance-wide migrations may not overlap.
     * Verified directly on the lock registry - the migration services share the same lock so
     * acquisition failures bubble up as MigrationException on the wrong-flavour caller.
     */
    @Test
    void instanceAndStationLocksAreMutuallyExclusive() {
        assertTrue(locks.tryAcquire(1));
        assertFalse(locks.tryAcquireInstance(), "instance lock acquisition must fail while a per-station lock is held");
        locks.release(1);

        assertTrue(locks.tryAcquireInstance());
        assertFalse(locks.tryAcquire(1), "per-station lock acquisition must fail while the instance lock is held");
        assertFalse(locks.tryAcquire(2), "no station can lock while the instance lock is held");
        locks.releaseInstance();

        assertTrue(locks.tryAcquire(1));
        assertTrue(locks.tryAcquire(2));
        locks.release(1);
        locks.release(2);
    }

    /**
     * Stations carrying their own backend override are skipped during the instance-wide copy -
     * their bytes already live somewhere other than the instance default.
     */
    @Test
    void stationsWithOverrideAreSkipped() {
        Station overridden = stationRepo.create("Overridden");
        Station inherited = stationRepo.create("Inherited");
        var overriddenScope = new StorageScope.Station(overridden.id(), overridden.uid());
        var inheritedScope = new StorageScope.Station(inherited.id(), inherited.uid());
        storageService.store(
                overriddenScope,
                StorageCategory.MEDIA_FILES,
                "ignored.txt",
                "x".getBytes(StandardCharsets.UTF_8),
                "text/plain");
        storageService.store(
                inheritedScope,
                StorageCategory.MEDIA_FILES,
                "carry.txt",
                "y".getBytes(StandardCharsets.UTF_8),
                "text/plain");

        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        storageConfigRepo.upsert(
                overridden.id(),
                new StationStorageBackendConfig.S3Variant(
                        "https://s3.example.invalid",
                        "us-east-1",
                        "bucket",
                        true,
                        Optional.empty(),
                        "/",
                        cipher.encrypt("{\"accessKey\":\"a\",\"secretKey\":\"b\"}")));

        StorageBackendSettings targetSettings = newLocalSettings(targetRoot.toString());
        var result = migrationService.commit(migrationService.prepare(targetSettings), true);

        assertEquals(1, result.copied(), "only the inherited station's bytes should be copied");
        storageConfigRepo.delete(overridden.id());
    }

    /**
     * Avatars (the only account-scoped movable category) ride along with the instance default
     * and get copied to the new target alongside station bytes.
     */
    @Test
    void avatarsAreCopied() {
        Account account = accountRepo.create("avatar@xfer.test", "First", "Last", true);
        var avatarScope = new StorageScope.Account(account.uid());
        storageService.store(
                avatarScope,
                StorageCategory.IMAGE_AVATAR,
                "doesnt-matter",
                "avatar".getBytes(StandardCharsets.UTF_8),
                "image/png");

        StorageBackendSettings targetSettings = newLocalSettings(targetRoot.toString());
        var result = migrationService.commit(migrationService.prepare(targetSettings), true);

        assertTrue(result.copied() >= 1, "avatar bytes should be migrated");
    }

    /**
     * Preparing a migration twice without releasing the first answers with a clear error so
     * the route handler can map it to 409 Conflict.
     */
    @Test
    void doublePrepareFailsLoud() {
        StorageBackendSettings targetSettings = newLocalSettings(targetRoot.toString());
        var prepared = migrationService.prepare(targetSettings);
        try {
            assertThrows(MigrationException.class, () -> migrationService.prepare(targetSettings));
        } finally {
            migrationService.abort(prepared);
        }
    }

    /**
     * The read-only flag is the outward signal that a migration owns the instance; the status
     * endpoint reads it, so it must be false before preparation and false again after commit.
     */
    @Test
    void inFlightFlagTracksThePreparedWindow() {
        assertFalse(migrationService.isMigrationInFlight());
        var prepared = migrationService.prepare(newLocalSettings(targetRoot.toString()));
        assertTrue(migrationService.isMigrationInFlight());
        migrationService.commit(prepared, true);
        assertFalse(migrationService.isMigrationInFlight());
    }

    /**
     * A read-only flag left set by an earlier crash blocks preparation and hands the instance
     * lock straight back, so a retry after the operator clears it is not deadlocked.
     */
    @Test
    void preparationRefusesWhileTheReadOnlyFlagIsAlreadySet() {
        assertTrue(readOnly.lock());

        var error = assertThrows(
                MigrationException.class, () -> migrationService.prepare(newLocalSettings(targetRoot.toString())));

        assertTrue(error.getMessage().contains("read-only"));
        assertFalse(locks.isInstanceLocked(), "the instance lock must be handed back");
        readOnly.unlock();
    }

    /**
     * A target that cannot be written to at all fails its probe; the lock and the read-only flag
     * are released so the instance keeps serving from the previous backend.
     */
    @Test
    void anUnhealthyTargetAbortsPreparation() throws Exception {
        Path blocked = Files.createTempFile("instance-migration-blocked", ".marker");

        var error = assertThrows(
                MigrationException.class, () -> migrationService.prepare(newLocalSettings(blocked.toString())));

        assertTrue(error.getMessage().contains("probe failed"));
        assertFalse(readOnly.isLocked());
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Migrating onto the backend that is already the instance default would delete the very bytes
     * it just "copied"; preparation refuses instead.
     */
    @Test
    void migratingOntoTheCurrentInstanceDefaultIsRefused() throws Exception {
        Storage sameRoot = new Storage();
        setField(Storage.class, sameRoot, "backend", new StorageBackendSettings());
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var sameRootFactory = new SwappableFactory(sameRoot, sourceBackend, cipher);
        var service = new InstanceStorageMigrationService(
                stationRepo,
                accountRepo,
                storageConfigRepo,
                new ClusterStationStorageRepository(),
                sameRootFactory,
                locks,
                readOnly);

        var error = assertThrows(MigrationException.class, () -> service.prepare(new StorageBackendSettings()));

        assertTrue(error.getMessage().contains("current instance default"));
        assertFalse(readOnly.isLocked());
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Bytes placed on the target out-of-band carry no hash in their sidecar. The comparison falls
     * back to hashing both sides, and an identical payload still counts as skipped.
     */
    @Test
    void keysOnTheTargetWithoutAStoredHashAreComparedByContent() {
        Station station = stationRepo.create("Hashless Target");
        byte[] payload = "hashless-payload".getBytes(StandardCharsets.UTF_8);
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(scope, StorageCategory.MEDIA_FILES, "doc.txt", payload, "text/plain");
        String fullKey = scope.prefix() + "/" + StorageCategory.MEDIA_FILES.prefix() + "/doc.txt";
        targetBackend.store(
                fullKey, new ByteArrayInputStream(payload), payload.length, ObjectMetadata.of("text/plain"));

        var result = migrationService.commit(migrationService.prepare(newLocalSettings(targetRoot.toString())), true);

        assertEquals(1, result.skipped(), "identical content must be recognised without a stored hash");
        assertEquals(0, result.copied());
    }

    /**
     * A target that accepts writes but cannot read them back fails sample verification. The
     * exception surfaces from preparation, before the caller ever flips the configuration.
     */
    @Test
    void sampleVerificationFailureAbortsPreparation() {
        Station station = stationRepo.create("Verify Failure Station");
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(
                scope, StorageCategory.MEDIA_FILES, "doc.txt", "verify".getBytes(StandardCharsets.UTF_8), "text/plain");
        factory.instanceTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public boolean exists(String fullKey) {
                return false;
            }
        };

        var error = assertThrows(
                MigrationException.class, () -> migrationService.prepare(newLocalSettings(targetRoot.toString())));

        assertTrue(error.getMessage().contains("Sample verification failed"));
        assertFalse(readOnly.isLocked(), "a failed preparation must clear the read-only flag");
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Deleting the source is best-effort: keys the old backend refuses to drop are logged and
     * left behind rather than failing a migration whose bytes are already safely on the target.
     */
    @Test
    void sourceDeleteFailuresDoNotFailTheCommit() {
        Station station = stationRepo.create("Delete Failure Station");
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(
                scope,
                StorageCategory.MEDIA_FILES,
                "doc.txt",
                "stubborn".getBytes(StandardCharsets.UTF_8),
                "text/plain");
        factory.instanceBackend = new LocalStorageBackend(sourceRoot) {
            @Override
            public void delete(String fullKey) {
                throw new StorageException("read-only filesystem");
            }
        };

        var result = migrationService.commit(migrationService.prepare(newLocalSettings(targetRoot.toString())), false);

        assertEquals(1, result.copied());
        assertEquals(0, result.deleted(), "keys that could not be deleted are not counted");
        assertFalse(readOnly.isLocked());
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Backends that blow up while releasing their connections must not strand the instance in
     * read-only mode - the flag and the lock are released regardless.
     */
    @Test
    void backendsThatFailToCloseStillReleaseTheInstance() {
        factory.instanceBackend = new LocalStorageBackend(sourceRoot) {
            @Override
            public void close() {
                throw new IllegalStateException("source close failed");
            }
        };
        factory.instanceTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public void close() {
                throw new IllegalStateException("target close failed");
            }
        };

        migrationService.commit(migrationService.prepare(newLocalSettings(targetRoot.toString())), false);

        assertFalse(readOnly.isLocked());
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Same guarantee on the abort path, which the route handler takes when saving the new
     * configuration fails after a successful byte copy.
     */
    @Test
    void abortReleasesTheInstanceEvenWhenTheTargetFailsToClose() {
        factory.instanceTarget = new LocalStorageBackend(targetRoot) {
            @Override
            public void close() {
                throw new IllegalStateException("target close failed");
            }
        };

        migrationService.abort(migrationService.prepare(newLocalSettings(targetRoot.toString())));

        assertFalse(readOnly.isLocked());
        assertFalse(locks.isInstanceLocked());
    }

    /**
     * Factory stand-in that lets a test point the instance default and the migration target at
     * deliberately misbehaving local backends without needing a real remote endpoint.
     */
    private static final class SwappableFactory extends StorageBackendFactory {
        private StorageBackend instanceBackend;
        private StorageBackend instanceTarget;

        private SwappableFactory(Storage storage, LocalStorageBackend local, CredentialCipher cipher) {
            super(storage, local, cipher);
        }

        @Override
        public synchronized StorageBackend instanceDefault() {
            return instanceBackend != null ? instanceBackend : super.instanceDefault();
        }

        @Override
        public StorageBackend buildForInstance(StorageBackendSettings settings) {
            return instanceTarget != null ? instanceTarget : super.buildForInstance(settings);
        }
    }

    private static StorageBackendSettings newLocalSettings(String root) {
        try {
            var settings = new StorageBackendSettings();
            setField(StorageBackendSettings.class, settings, "type", StorageBackendType.LOCAL);
            setField(StorageBackendSettings.LocalSettings.class, settings.local(), "root", root);
            return settings;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void setField(Class<?> clazz, Object target, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static byte[] readKey(LocalStorageBackend backend, String stationUid, String key) {
        String fullKey = "station/" + stationUid + "/" + StorageCategory.MEDIA_FILES.prefix() + "/" + key;
        try (var stream = backend.read(fullKey).orElseThrow()) {
            return stream.body().readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
