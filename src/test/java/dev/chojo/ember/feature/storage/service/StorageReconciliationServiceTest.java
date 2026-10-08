/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.transfer.ActiveImports;
import dev.chojo.ember.feature.station.transfer.ImportProgress;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.StoredCredentials;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;

/**
 * Verifies that reconciliation removes on-disk files whose owning database row is gone, and
 * leaves files alone whose row still exists, unless a transfer holds the station or its storage is
 * shared with the installation it moved here from.
 */
@Tag("database")
class StorageReconciliationServiceTest extends RepositoryTestBase {

    @TempDir
    static Path storageRoot;

    private static StorageService storageService;
    private static StationStorageConfigRepository configRepo;
    private static ActiveImports activeImports;
    private static StorageReconciliationService reconciliation;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        var resolver = new StorageBackendResolver(backend);
        storageService = spy(new StorageService(resolver, backend));
        configRepo = new StationStorageConfigRepository();
        activeImports = new ActiveImports();
        reconciliation = new StorageReconciliationService(
                storageUsageRepo, stationRepo, storageService, configRepo, activeImports, new Storage());
    }

    @AfterAll
    static void cleanup() throws IOException {
        if (storageRoot != null && Files.exists(storageRoot)) {
            try (var walk = Files.walk(storageRoot)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    @Test
    void reconciliationRemovesOrphanMediaHashes() {
        var station = stationRepo.create("Orphan Cleanup MEDIA_FILES");
        try {
            var scope = new StorageScope.Station(station.id(), station.uid());

            String liveHash = "a".repeat(64);
            mediaFileRepo.create(null, station.id(), liveHash, "live.txt", "text/plain", 5);
            storageService.store(
                    scope, StorageCategory.MEDIA_FILES, liveHash + "/orig.txt", "alive".getBytes(), "text/plain");

            String orphanHash = "b".repeat(64);
            storageService.store(
                    scope, StorageCategory.MEDIA_FILES, orphanHash + "/orig.txt", "dead".getBytes(), "text/plain");

            reconciliation.reconcileStation(station.id());

            assertTrue(
                    storageService.exists(scope, StorageCategory.MEDIA_FILES, liveHash + "/orig.txt"),
                    "live hash file must survive reconciliation");
            assertFalse(
                    storageService.exists(scope, StorageCategory.MEDIA_FILES, orphanHash + "/orig.txt"),
                    "orphan hash file must be deleted by reconciliation");
        } finally {
            stationRepo.delete(station.id());
        }
    }

    /**
     * The copy a station leaves behind when it moves away shares its place with the station at its new
     * address, whose files no row of the copy names.
     */
    @Test
    void aStationThatMovedAwayKeepsEveryFile() {
        var station = stationRepo.create("Moved away");
        try {
            String orphan = storeOrphan(station, StorageCategory.MEDIA_FILES);
            stationRepo.markMovedAway(station.id(), "https://elsewhere.example");

            reconciliation.reconcileStation(station.id());

            assertTrue(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));
        } finally {
            stationRepo.delete(station.id());
        }
    }

    /** While the destination pulls a station over, it may already write into the place both share. */
    @Test
    void aStationBeingHandedOverKeepsEveryFile() {
        var station = stationRepo.create("Handed over");
        try {
            String orphan = storeOrphan(station, StorageCategory.MEDIA_FILES);
            stationRepo.markReadOnlyForTransfer(station.id());

            reconciliation.reconcileStation(station.id());

            assertTrue(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));
        } finally {
            stationRepo.delete(station.id());
        }
    }

    /**
     * An import still writing into a station has not brought all rows yet, so the files of the rows
     * still to come look orphaned. They stay until the import has ended, and only then does a file no row
     * names go.
     */
    @Test
    void aStationAnImportStillWritesIntoKeepsEveryFileUntilItEnds() {
        var station = stationRepo.create("Arriving");
        try {
            String orphan = storeOrphan(station, StorageCategory.MEDIA_FILES);
            var progress = new ImportProgress(
                    station.id(),
                    station.uid(),
                    station.name(),
                    List.of(),
                    "https://source.example",
                    "token",
                    ImportProgress.Target.NEW_STATION);
            activeImports.start(progress);

            reconciliation.reconcileStation(station.id());
            assertTrue(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));

            progress.complete();
            reconciliation.reconcileStation(station.id());
            assertFalse(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));
        } finally {
            activeImports.forget(station.id());
            stationRepo.delete(station.id());
        }
    }

    /**
     * A transfer that begins while a station is being reconciled stops the reconciliation at the next
     * category: what was already looked at is done, and nothing after it is touched.
     */
    @Test
    void aTransferBeginningMidwayStopsTheReconciliation() {
        var station = stationRepo.create("Midway");
        try {
            String earlier = storeOrphan(station, StorageCategory.MEDIA_FILES);
            String later = storeOrphan(station, StorageCategory.KB_FILES);
            doAnswer(call -> {
                        stationRepo.markReadOnlyForTransfer(station.id());
                        return call.callRealMethod();
                    })
                    .when(storageService)
                    .listKeys(eq(scope(station)), eq(StorageCategory.MEDIA_FILES), anyString());

            reconciliation.reconcileStation(station.id());

            assertFalse(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, earlier));
            assertTrue(storageService.exists(scope(station), StorageCategory.KB_FILES, later));
        } finally {
            reset(storageService);
            stationRepo.delete(station.id());
        }
    }

    /**
     * Storage a station took over from the installation it moved here from can still hold that
     * installation's files, so nothing there is deleted, while the usage is still counted.
     */
    @Test
    void storageSharedWithTheInstallationLeftBehindLosesNoFile() {
        var station = stationRepo.create("Shared storage");
        try {
            String orphan = storeOrphan(station, StorageCategory.MEDIA_FILES);
            configRepo.adoptFromTransfer(station.id(), sharedS3());

            reconciliation.reconcileStation(station.id());

            assertTrue(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));
            assertEquals(
                    1,
                    storageUsageRepo
                            .findByStationAndCategory(station.id(), StorageCategory.MEDIA_FILES)
                            .orElseThrow()
                            .fileCount());

            configRepo.upsert(station.id(), sharedS3());
            reconciliation.reconcileStation(station.id());
            assertFalse(storageService.exists(scope(station), StorageCategory.MEDIA_FILES, orphan));
        } finally {
            stationRepo.delete(station.id());
        }
    }

    private static StorageScope.Station scope(Station station) {
        return new StorageScope.Station(station.id(), station.uid());
    }

    /** Stores a file of the category that no row of the station names, and returns its key. */
    private static String storeOrphan(Station station, StorageCategory category) {
        String key = (category == StorageCategory.MEDIA_FILES ? "c".repeat(64) : "999999999") + "/orig.txt";
        storageService.store(scope(station), category, key, "orphan".getBytes(), "text/plain");
        return key;
    }

    private static StationStorageBackendConfig sharedS3() {
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        return new StationStorageBackendConfig.S3Variant(
                "https://s3.example.invalid",
                "us-east-1",
                "shared-bucket",
                true,
                Optional.empty(),
                "",
                cipher.encrypt(new StoredCredentials.S3("access", "secret").toJson()));
    }

    @Test
    void reconciliationLeavesUnknownCategoriesAlone() {
        var station = stationRepo.create("Orphan Cleanup IMAGE_KB_IMAGE");
        try {
            var scope = new StorageScope.Station(station.id(), station.uid());

            String inlineId = "file-1-1700000000000";
            storageService.store(
                    scope,
                    StorageCategory.IMAGE_KB_IMAGE,
                    inlineId + "/original.png",
                    new byte[] {(byte) 0x89, 'P', 'N', 'G'},
                    "image/png");

            reconciliation.reconcileStation(station.id());

            assertTrue(
                    storageService.exists(scope, StorageCategory.IMAGE_KB_IMAGE, inlineId + "/original.png"),
                    "untracked categories must not be swept");
        } finally {
            stationRepo.delete(station.id());
        }
    }
}
