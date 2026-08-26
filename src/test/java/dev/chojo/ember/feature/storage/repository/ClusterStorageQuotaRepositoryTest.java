/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.ClusterStationQuota;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room a cluster hands out: its defaults, its tiers and what each station was granted.
 */
class ClusterStorageQuotaRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final long GIB = 1024L * 1024 * 1024;

    private static ClusterStorageQuotaRepository repository;

    @BeforeAll
    static void setup() {
        repository = new ClusterStorageQuotaRepository();
    }

    private static int freshCluster() {
        return clusterService
                .create("Kreisverband Platz " + NAMES.incrementAndGet(), null)
                .id();
    }

    @Test
    void aClusterThatSetNoDefaultsHasNone() {
        int clusterId = freshCluster();

        var defaults = repository.findDefaults(clusterId);

        assertEquals(clusterId, defaults.clusterId());
        assertNull(defaults.quotaBytes(), "nothing was set, so nothing stands");
        assertNull(defaults.perImageBytes());
    }

    @Test
    void defaultsAreReadBackFromTheClusterAndFromItsStations() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Platz " + NAMES.incrementAndGet());

        repository.setDefaults(new ClusterQuotaDefaults(clusterId, 4 * GIB, 3 * GIB, null, null, null, null, null));

        assertEquals(4 * GIB, repository.findDefaults(clusterId).quotaBytes());
        var fromStation = repository.findDefaultsForStation(station.id()).orElseThrow();
        assertEquals(3 * GIB, fromStation.quotaKbBytes());
        assertNull(fromStation.quotaBoardBytes(), "a dimension the cluster left alone stays open");

        clusterService.releaseStation(clusterId, station.id());
        assertTrue(
                repository.findDefaultsForStation(station.id()).isEmpty(),
                "a station that answers to nobody reads nobody's defaults");
        stationRepo.delete(station.id());
    }

    @Test
    void aGrantIsWrittenReadBackAndTakenAway() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Zuteilung " + NAMES.incrementAndGet());

        assertTrue(repository.findGrant(station.id()).isEmpty(), "nothing was granted yet");

        repository.setGrant(new ClusterStationQuota(
                station.id(), clusterId, 2 * GIB, null, null, null, null, 50L * 1024 * 1024, null, null));

        var grant = repository.findGrant(station.id()).orElseThrow();
        assertEquals(2 * GIB, grant.quotaBytes());
        assertEquals(50L * 1024 * 1024, grant.perFileBytes());
        assertNull(grant.quotaKbBytes(), "a dimension left out falls back to the cluster");
        assertNull(grant.presetId());

        assertTrue(repository.deleteGrant(station.id()));
        assertFalse(repository.deleteGrant(station.id()), "there was nothing left to take away");

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aSecondGrantReplacesTheFirstRatherThanAddingToIt() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Neuzuteilung " + NAMES.incrementAndGet());

        repository.setGrant(
                new ClusterStationQuota(station.id(), clusterId, 2 * GIB, 1 * GIB, null, null, null, null, null, null));
        repository.setGrant(
                new ClusterStationQuota(station.id(), clusterId, 5 * GIB, null, null, null, null, null, null, null));

        var grant = repository.findGrant(station.id()).orElseThrow();
        assertEquals(5 * GIB, grant.quotaBytes());
        assertNull(grant.quotaKbBytes(), "the whole grant is replaced, not merged with what was there");
        assertEquals(1, repository.findGrants(clusterId).size());

        repository.deleteGrant(station.id());
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void whatIsPromisedAddsUpAndCanLeaveOneStationOut() {
        int clusterId = freshCluster();
        var first = clusterService.createStation(clusterId, "Wache Summe A " + NAMES.incrementAndGet());
        var second = clusterService.createStation(clusterId, "Wache Summe B " + NAMES.incrementAndGet());

        repository.setGrant(
                new ClusterStationQuota(first.id(), clusterId, 2 * GIB, null, null, null, null, null, null, null));
        repository.setGrant(
                new ClusterStationQuota(second.id(), clusterId, 3 * GIB, null, null, null, null, null, null, null));

        assertEquals(5 * GIB, repository.sumGrantedTotals(clusterId, 0));
        assertEquals(
                3 * GIB,
                repository.sumGrantedTotals(clusterId, first.id()),
                "the station about to be granted again is weighed out of the sum");

        for (var station : List.of(first, second)) {
            repository.deleteGrant(station.id());
            clusterService.releaseStation(clusterId, station.id());
            stationRepo.delete(station.id());
        }
    }

    @Test
    void aGrantWithoutATotalPromisesNothingOutOfThePool() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Ohne Summe " + NAMES.incrementAndGet());

        repository.setGrant(
                new ClusterStationQuota(station.id(), clusterId, null, 1 * GIB, null, null, null, null, null, null));

        assertEquals(
                0L,
                repository.sumGrantedTotals(clusterId, 0),
                "a grant that names no total takes nothing out of the pool");

        repository.deleteGrant(station.id());
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aTierIsHandedToSeveralStationsAtOnceAndSurvivesItsOwnDeletion() {
        int clusterId = freshCluster();
        var first = clusterService.createStation(clusterId, "Wache Stufe A " + NAMES.incrementAndGet());
        var second = clusterService.createStation(clusterId, "Wache Stufe B " + NAMES.incrementAndGet());

        var preset = repository.createPreset(
                clusterId, "Standard", 4 * GIB, 3 * GIB, 2 * GIB, GIB, GIB, 50L * 1024 * 1024, 5L * 1024 * 1024);
        repository.applyPreset(preset.id(), clusterId, List.of(first.id(), second.id()));

        var grant = repository.findGrant(first.id()).orElseThrow();
        assertEquals(4 * GIB, grant.quotaBytes());
        assertEquals(2 * GIB, grant.quotaBoardBytes());
        assertEquals(preset.id(), grant.presetId());
        assertEquals(8 * GIB, repository.sumGrantedTotals(clusterId, 0));

        assertTrue(repository.deletePreset(preset.id()));
        var afterDelete = repository.findGrant(second.id()).orElseThrow();
        assertEquals(4 * GIB, afterDelete.quotaBytes(), "the numbers a station lives on survive the tier");
        assertNull(afterDelete.presetId(), "only the name of the tier is gone");

        for (var station : List.of(first, second)) {
            repository.deleteGrant(station.id());
            clusterService.releaseStation(clusterId, station.id());
            stationRepo.delete(station.id());
        }
    }

    @Test
    void aTierIsRenamedAndItsNumbersChangedWithoutMovingAnybody() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Stufe C " + NAMES.incrementAndGet());

        var preset = repository.createPreset(clusterId, "Klein", GIB, GIB, GIB, GIB, GIB, 1024, 1024);
        repository.applyPreset(preset.id(), clusterId, List.of(station.id()));
        repository.updatePreset(preset.id(), "Mittel", 2 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);

        assertEquals("Mittel", repository.findPreset(preset.id()).orElseThrow().name());
        assertEquals(
                GIB,
                repository.findGrant(station.id()).orElseThrow().quotaBytes(),
                "editing a tier does not move the stations already on it");
        assertEquals(1, repository.findPresets(clusterId).size());

        repository.deleteGrant(station.id());
        repository.deletePreset(preset.id());
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }
}
