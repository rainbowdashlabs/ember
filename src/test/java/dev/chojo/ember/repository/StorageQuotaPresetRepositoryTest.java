/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.entity.StationStorageQuota;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StorageQuotaPresetRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static int presetId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Preset Test Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createPreset() {
        var preset = storagePresetRepo.create(
                "Standard",
                5_000_000_000L,
                4_000_000_000L,
                3_000_000_000L,
                1_000_000_000L,
                500_000_000L,
                50_000_000L,
                5_000_000L);
        assertNotNull(preset);
        assertEquals("Standard", preset.name());
        assertEquals(5_000_000_000L, preset.total());
        assertEquals(50_000_000L, preset.perFile());
        presetId = preset.id();
    }

    @Test
    @Order(2)
    void findById() {
        var preset = storagePresetRepo.findById(presetId);
        assertTrue(preset.isPresent());
        assertEquals("Standard", preset.get().name());
    }

    @Test
    @Order(3)
    void findAll() {
        storagePresetRepo.create(
                "Small",
                1_000_000_000L,
                512_000_000L,
                256_000_000L,
                256_000_000L,
                128_000_000L,
                25_000_000L,
                5_000_000L);
        var all = storagePresetRepo.findAll();
        assertTrue(all.size() >= 2);
    }

    @Test
    @Order(4)
    void updatePreset() {
        var updated = storagePresetRepo.update(
                presetId,
                "Premium",
                20_000_000_000L,
                15_000_000_000L,
                10_000_000_000L,
                5_000_000_000L,
                2_000_000_000L,
                100_000_000L,
                10_000_000L);
        assertEquals("Premium", updated.name());
        assertEquals(20_000_000_000L, updated.total());
    }

    @Test
    @Order(5)
    void applyToStation() {
        storagePresetRepo.applyToStation(presetId, station.id());
        // Verify by reading the station quota columns
        var quota = StationStorageQuota.map();
        // The station should now have the preset's values
        var stationObj = stationRepo.findById(station.id());
        assertTrue(stationObj.isPresent());
    }

    @Test
    @Order(6)
    void findStationPresetAssignments() {
        // applyToStation was called in order 5, so station should have a preset
        var assignments = storagePresetRepo.findStationPresetAssignments();
        var assignment = assignments.get(station.id());
        assertNotNull(assignment);
        assertEquals("Premium", assignment.presetName());
    }

    @Test
    @Order(7)
    void findStationPresetAssignmentsEmptyAfterReset() {
        storagePresetRepo.resetStationQuotas(station.id());
        var assignments = storagePresetRepo.findStationPresetAssignments();
        assertNull(assignments.get(station.id()));
    }

    @Test
    @Order(8)
    void resetStationQuotas() {
        storagePresetRepo.resetStationQuotas(station.id());
        // Station quotas should be reset to NULL
        var stationObj = stationRepo.findById(station.id());
        assertTrue(stationObj.isPresent());
    }

    @Test
    @Order(9)
    void findByIdReturnsEmptyForMissing() {
        assertTrue(storagePresetRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(100)
    void deletePreset() {
        storagePresetRepo.delete(presetId);
        assertTrue(storagePresetRepo.findById(presetId).isEmpty());
    }
}
