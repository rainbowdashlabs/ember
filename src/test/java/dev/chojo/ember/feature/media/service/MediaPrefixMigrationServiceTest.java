/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.ObjectMetadata;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.migration.MigrationLockRegistry;
import dev.chojo.ember.feature.storage.service.StorageReconciliationService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The move from the {@code page-files} prefix onto {@code media/files}. A local backend stands in
 * for the real one, which is enough to exercise the copy, the resumable skip, the source delete
 * and the lock, without needing a remote target.
 */
class MediaPrefixMigrationServiceTest extends RepositoryTestBase {

    @TempDir
    Path root;

    private LocalStorageBackend backend;
    private StorageService storageService;
    private MigrationLockRegistry locks;
    private MediaPrefixMigrationService migration;
    private final List<Station> created = new ArrayList<>();

    @BeforeEach
    void setup() {
        backend = new LocalStorageBackend(root);
        var resolver = new StorageBackendResolver(backend);
        storageService = new StorageService(resolver, backend);
        locks = new MigrationLockRegistry();
        var reconciliation =
                new StorageReconciliationService(storageUsageRepo, stationRepo, storageService, new Storage());
        migration = new MediaPrefixMigrationService(stationRepo, resolver, reconciliation, locks);
    }

    @AfterEach
    void cleanup() {
        for (var station : created) {
            stationRepo.delete(station.id());
        }
        created.clear();
    }

    /**
     * A station with one media row, because the usage reconciliation the move ends with clears
     * away anything on disk that no row claims. Bytes without a row are orphans by definition.
     */
    private Station station(String name) {
        var station = stationRepo.create(name);
        created.add(station);
        mediaFileRepo.create(null, station.id(), "hash", "orig.txt", "text/plain", 9);
        return station;
    }

    /**
     * Writes bytes where the library used to keep them, which is the state every station is in
     * before the move runs.
     */
    private String writeFormer(Station station, String formerPrefix, String key, String payload) {
        String fullKey = "station/" + station.uid() + "/" + formerPrefix + "/" + key;
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        backend.store(fullKey, new ByteArrayInputStream(bytes), bytes.length, ObjectMetadata.of("text/plain"));
        return fullKey;
    }

    private byte[] readCurrent(Station station, StorageCategory category, String key) {
        var scope = new StorageScope.Station(station.id(), station.uid());
        return storageService.readAllBytes(scope, category, key).orElseThrow();
    }

    @Test
    void bytesMoveOntoTheMediaPrefixAndTheOldOneIsCleared() {
        var station = station("MediaPrefixMove");
        writeFormer(station, "page-files", "hash/orig.txt", "file-bytes");
        writeFormer(station, "page-images", "42/original.txt", "image-bytes");

        assertEquals(2, migration.migrateStation(station.id()));

        assertArrayEquals(
                "file-bytes".getBytes(StandardCharsets.UTF_8),
                readCurrent(station, StorageCategory.MEDIA_FILES, "hash/orig.txt"));
        assertArrayEquals(
                "image-bytes".getBytes(StandardCharsets.UTF_8),
                readCurrent(station, StorageCategory.MEDIA_IMAGES, "42/original.txt"));
        assertFalse(
                backend.exists("station/" + station.uid() + "/page-files/hash/orig.txt"),
                "the source is deleted once the copy is verified");
    }

    @Test
    void aStationWithoutAResolvableUuidIsSkippedRatherThanCrashed() {
        var unresolvable = Mockito.mock(StationRepository.class);
        Mockito.when(unresolvable.resolveUid(4711)).thenReturn(null);
        var skipping = new MediaPrefixMigrationService(
                unresolvable,
                new StorageBackendResolver(backend),
                new StorageReconciliationService(storageUsageRepo, stationRepo, storageService, new Storage()),
                locks);
        assertEquals(0, skipping.migrateStation(4711));
    }

    @Test
    void aStationWithNothingUnderTheOldPrefixIsLeftAlone() {
        var station = station("MediaPrefixNothingToDo");
        assertEquals(0, migration.migrateStation(station.id()));
        assertFalse(locks.isLocked(station.id()), "nothing to move means nothing to lock");
    }

    @Test
    void aSecondRunSkipsWhatIsAlreadyThere() {
        var station = station("MediaPrefixResume");
        writeFormer(station, "page-files", "hash/orig.txt", "resumable");

        assertEquals(1, migration.migrateStation(station.id()));
        // A crash mid-move leaves the source behind with the target already written.
        writeFormer(station, "page-files", "hash/orig.txt", "resumable");
        assertEquals(0, migration.migrateStation(station.id()), "matching bytes are skipped rather than recopied");
        assertArrayEquals(
                "resumable".getBytes(StandardCharsets.UTF_8),
                readCurrent(station, StorageCategory.MEDIA_FILES, "hash/orig.txt"));
    }

    @Test
    void differingBytesUnderTheSameKeyAreCopiedOverRatherThanSkipped() {
        var station = station("MediaPrefixOverwrite");
        writeFormer(station, "page-files", "hash/orig.txt", "the real bytes");
        var scope = new StorageScope.Station(station.id(), station.uid());
        storageService.store(
                scope,
                StorageCategory.MEDIA_FILES,
                "hash/orig.txt",
                "half written".getBytes(StandardCharsets.UTF_8),
                "text/plain");

        assertEquals(1, migration.migrateStation(station.id()));
        assertArrayEquals(
                "the real bytes".getBytes(StandardCharsets.UTF_8),
                readCurrent(station, StorageCategory.MEDIA_FILES, "hash/orig.txt"));
    }

    @Test
    void aStationAlreadyBeingMigratedIsRefusedRatherThanRunTwice() {
        var station = station("MediaPrefixLocked");
        writeFormer(station, "page-files", "hash/orig.txt", "locked");
        assertTrue(locks.tryAcquire(station.id()));
        try {
            assertThrows(MigrationException.class, () -> migration.migrateStation(station.id()));
        } finally {
            locks.release(station.id());
        }
    }

    @Test
    void everyStationIsWalkedAndOneFailureDoesNotStopTheRest() {
        var first = station("MediaPrefixAllOne");
        var second = station("MediaPrefixAllTwo");
        writeFormer(first, "page-files", "hash/orig.txt", "one");
        writeFormer(second, "page-files", "hash/orig.txt", "two");
        // A station whose lock is held fails; the other still has to be moved.
        assertTrue(locks.tryAcquire(first.id()));
        try {
            assertTrue(migration.migrateAll() >= 1);
        } finally {
            locks.release(first.id());
        }
        assertArrayEquals(
                "two".getBytes(StandardCharsets.UTF_8),
                readCurrent(second, StorageCategory.MEDIA_FILES, "hash/orig.txt"));
    }
}
