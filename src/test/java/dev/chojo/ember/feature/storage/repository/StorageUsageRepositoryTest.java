/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageUsageRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Storage Test Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void applyDeltaCreatesRecordOnFirstCall() {
        storageUsageRepo.applyDelta(station.id(), StorageCategory.KB_FILES, 1024, 1);
        var usage = storageUsageRepo.findByStationAndCategory(station.id(), StorageCategory.KB_FILES);
        assertTrue(usage.isPresent());
        assertEquals(1024, usage.get().totalBytes());
        assertEquals(1, usage.get().fileCount());
    }

    @Test
    @Order(2)
    void applyDeltaIncrementsExistingRecord() {
        storageUsageRepo.applyDelta(station.id(), StorageCategory.KB_FILES, 2048, 2);
        var usage = storageUsageRepo.findByStationAndCategory(station.id(), StorageCategory.KB_FILES);
        assertTrue(usage.isPresent());
        assertEquals(3072, usage.get().totalBytes());
        assertEquals(3, usage.get().fileCount());
    }

    @Test
    @Order(3)
    void applyNegativeDeltaDecrementsButFloorsAtZero() {
        storageUsageRepo.applyDelta(station.id(), StorageCategory.KB_FILES, -5000, -10);
        var usage = storageUsageRepo.findByStationAndCategory(station.id(), StorageCategory.KB_FILES);
        assertTrue(usage.isPresent());
        assertEquals(0, usage.get().totalBytes());
        assertEquals(0, usage.get().fileCount());
    }

    @Test
    @Order(4)
    void setUsageOverwritesExisting() {
        storageUsageRepo.setUsage(station.id(), StorageCategory.KB_FILES, 5000, 10);
        var usage = storageUsageRepo.findByStationAndCategory(station.id(), StorageCategory.KB_FILES);
        assertTrue(usage.isPresent());
        assertEquals(5000, usage.get().totalBytes());
        assertEquals(10, usage.get().fileCount());
    }

    @Test
    @Order(5)
    void findByStationReturnsAllCategories() {
        storageUsageRepo.setUsage(station.id(), StorageCategory.BOARD_ATTACHMENTS, 2000, 5);
        storageUsageRepo.setUsage(station.id(), StorageCategory.IMAGE_AVATAR, 500, 2);
        var usages = storageUsageRepo.findByStation(station.id());
        assertEquals(3, usages.size());
    }

    @Test
    @Order(6)
    void totalEnforcedBytesExcludesAvatars() {
        long total = storageUsageRepo.totalEnforcedBytes(station.id());
        assertEquals(7000, total);
    }

    @Test
    @Order(7)
    void categoryBytes() {
        assertEquals(5000, storageUsageRepo.categoryBytes(station.id(), StorageCategory.KB_FILES));
        assertEquals(2000, storageUsageRepo.categoryBytes(station.id(), StorageCategory.BOARD_ATTACHMENTS));
        assertEquals(0, storageUsageRepo.categoryBytes(station.id(), StorageCategory.MEDIA_IMAGES));
    }

    @Test
    @Order(8)
    void findAll() {
        var all = storageUsageRepo.findAll();
        assertTrue(all.size() >= 3);
    }

    @Test
    @Order(9)
    void findByStationAndCategoryReturnsEmptyForMissing() {
        assertTrue(storageUsageRepo
                .findByStationAndCategory(station.id(), StorageCategory.IMAGE_QUIZ_QUESTION)
                .isEmpty());
        assertTrue(storageUsageRepo
                .findByStationAndCategory(99999, StorageCategory.KB_FILES)
                .isEmpty());
    }

    @Test
    @Order(100)
    void deleteByStation() {
        storageUsageRepo.deleteByStation(station.id());
        assertTrue(storageUsageRepo.findByStation(station.id()).isEmpty());
    }
}
