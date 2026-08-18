/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that reconciliation removes on-disk files whose owning database row is gone, and
 * leaves files alone whose row still exists.
 */
@Tag("database")
class StorageReconciliationServiceTest extends RepositoryTestBase {

    @TempDir
    static Path storageRoot;

    private static StorageService storageService;
    private static StorageReconciliationService reconciliation;

    @BeforeAll
    static void setup() {
        var backend = new LocalStorageBackend(storageRoot);
        var resolver = new StorageBackendResolver(backend);
        storageService = new StorageService(resolver, backend);
        reconciliation = new StorageReconciliationService(storageUsageRepo, stationRepo, storageService, new Storage());
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
    void reconciliationRemovesOrphanPageFileHashes() {
        var station = stationRepo.create("Orphan Cleanup PAGE_FILES");
        try {
            var scope = new StorageScope.Station(station.id(), station.uid());

            // Live page-file row with hash "live-hash" - file under that hash must survive.
            String liveHash = "a".repeat(64);
            pageRepo.createFile(null, station.id(), liveHash, "live.txt", "text/plain", 5);
            storageService.store(
                    scope, StorageCategory.PAGE_FILES, liveHash + "/orig.txt", "alive".getBytes(), "text/plain");

            // Orphan hash with no DB row - file under that hash must be deleted.
            String orphanHash = "b".repeat(64);
            storageService.store(
                    scope, StorageCategory.PAGE_FILES, orphanHash + "/orig.txt", "dead".getBytes(), "text/plain");

            reconciliation.reconcileStation(station.id());

            assertTrue(
                    storageService.exists(scope, StorageCategory.PAGE_FILES, liveHash + "/orig.txt"),
                    "live hash file must survive reconciliation");
            assertFalse(
                    storageService.exists(scope, StorageCategory.PAGE_FILES, orphanHash + "/orig.txt"),
                    "orphan hash file must be deleted by reconciliation");
        } finally {
            stationRepo.delete(station.id());
        }
    }

    @Test
    void reconciliationLeavesUnknownCategoriesAlone() {
        var station = stationRepo.create("Orphan Cleanup IMAGE_KB_IMAGE");
        try {
            var scope = new StorageScope.Station(station.id(), station.uid());

            // KB inline images have no DB tracking; reconciliation must NOT delete them blindly.
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
