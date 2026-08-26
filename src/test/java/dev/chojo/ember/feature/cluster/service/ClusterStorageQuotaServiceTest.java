/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.cluster.service.ClusterStorageQuotaService.Dimensions;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The room a cluster hands out: what it promises, to whom, and what the pool will bear.
 */
class ClusterStorageQuotaServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final long GIB = 1024L * 1024 * 1024;

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Platzverteilung " + NAMES.incrementAndGet(), null)
                .id();
    }

    private static Dimensions total(Long bytes) {
        return new Dimensions(bytes, null, null, null, null, null, null);
    }

    // -- The pool --

    @Test
    void aClusterCannotHandOutMoreRoomThanItHas() {
        int clusterId = freshCluster();
        clusterStorageQuotaService.setStoragePool(clusterId, 1_000L);
        var first = clusterService.createStation(clusterId, "Wache Voll A " + NAMES.incrementAndGet());
        var second = clusterService.createStation(clusterId, "Wache Voll B " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setTotal(clusterId, first.id(), 800L);
        var refused = assertThrows(
                BadRequestResponse.class, () -> clusterStorageQuotaService.setTotal(clusterId, second.id(), 300L));
        assertTrue(refused.getMessage().contains("more than the cluster has left"));

        // What fits is allowed
        clusterStorageQuotaService.setTotal(clusterId, second.id(), 200L);
        var overview = clusterStorageQuotaService.findOverview(clusterId);
        assertEquals(1_000L, overview.poolBytes());
        assertEquals(1_000L, overview.handedOut());

        clusterService.releaseStation(clusterId, first.id());
        clusterService.releaseStation(clusterId, second.id());
        stationRepo.delete(first.id());
        stationRepo.delete(second.id());
    }

    @Test
    void aClusterWithNoPoolHasNothingToRunOutOf() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Unbegrenzt " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 999_999_999L);

        assertNull(clusterStorageQuotaService.findOverview(clusterId).poolBytes());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void theClustersOwnStoreIsPromisedOutOfTheSamePool() {
        int clusterId = freshCluster();
        var cluster = clusterRepo.findById(clusterId).orElseThrow();
        clusterStorageQuotaService.setStoragePool(clusterId, 1_000L);
        var station = clusterService.createStation(clusterId, "Wache Neben Verband " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setTotal(clusterId, cluster.homeStationId(), 600L);
        var refused = assertThrows(
                BadRequestResponse.class, () -> clusterStorageQuotaService.setTotal(clusterId, station.id(), 500L));
        assertTrue(refused.getMessage().contains("more than the cluster has left"));

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 400L);
        var overview = clusterStorageQuotaService.findOverview(clusterId);
        assertEquals(1_000L, overview.handedOut(), "what the cluster keeps for itself is part of what it handed out");
        assertEquals(2, overview.stations().size(), "its own store is one of the stations on the list");
        assertTrue(
                overview.stations().stream().anyMatch(ClusterStorageQuotaService.StationRoom::ownStore),
                "and it says which one it is");

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    // -- Grants --

    @Test
    void handingTheRoomBackIsAlwaysAllowed() {
        int clusterId = freshCluster();
        clusterStorageQuotaService.setStoragePool(clusterId, 100L);
        var station = clusterService.createStation(clusterId, "Wache Zurueckgabe " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 100L);
        clusterStorageQuotaService.setTotal(clusterId, station.id(), null);

        assertEquals(0L, clusterStorageQuotaService.findOverview(clusterId).handedOut());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aGrantIsTheClustersOwnAndGoesWithTheMembership() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Unberuehrt " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 700L);

        assertEquals(
                700L,
                clusterStorageQuotaRepo.findGrant(station.id()).orElseThrow().quotaBytes(),
                "the cluster writes its own numbers rather than the instance's");

        clusterService.releaseStation(clusterId, station.id());
        assertTrue(
                clusterStorageQuotaRepo.findGrant(station.id()).isEmpty(),
                "a station that has been let go was promised nothing");
        assertEquals(
                0L,
                clusterStorageQuotaService.findOverview(clusterId).handedOut(),
                "and the room comes back when the station goes");
        stationRepo.delete(station.id());
    }

    @Test
    void aClusterCannotHandRoomToAStationThatIsNotItsOwn() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var station = clusterService.createStation(otherClusterId, "Wache Fremd " + NAMES.incrementAndGet());

        assertThrows(BadRequestResponse.class, () -> clusterStorageQuotaService.setTotal(clusterId, station.id(), 10L));
        assertThrows(
                BadRequestResponse.class,
                () -> clusterStorageQuotaService.setGrant(clusterId, station.uid(), total(10L)));

        clusterService.releaseStation(otherClusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void roomCannotBeHandedToAStationThatDoesNotExist() {
        int clusterId = freshCluster();

        assertThrows(NotFoundResponse.class, () -> clusterStorageQuotaService.setTotal(clusterId, 999_999, 1L));
    }

    @Test
    void aClusterThatIsNotThereHandsOutNothing() {
        assertThrows(NotFoundResponse.class, () -> clusterStorageQuotaService.setStoragePool(999_999, 1L));
        assertThrows(NotFoundResponse.class, () -> clusterStorageQuotaService.findOverview(999_999));
    }

    @Test
    void aGrantCanNameEveryDimensionAndBeTakenBackWhole() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Alle Masse " + NAMES.incrementAndGet());

        clusterStorageQuotaService.setGrant(
                clusterId,
                station.uid(),
                new Dimensions(5 * GIB, 2 * GIB, GIB, GIB, GIB, 20L * 1024 * 1024, 2L * 1024 * 1024));

        var grant = clusterStorageQuotaRepo.findGrant(station.id()).orElseThrow();
        assertEquals(2 * GIB, grant.quotaKbBytes());
        assertEquals(20L * 1024 * 1024, grant.perFileBytes());

        clusterStorageQuotaService.handBack(clusterId, station.uid());
        assertTrue(clusterStorageQuotaRepo.findGrant(station.id()).isEmpty());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void roomBelowNothingIsATypingMistake() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Negativ " + NAMES.incrementAndGet());

        assertThrows(
                BadRequestResponse.class,
                () -> clusterStorageQuotaService.setGrant(clusterId, station.uid(), total(-1L)));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    // -- Defaults --

    @Test
    void theDefaultsAreWrittenReadBackAndNotWeighedAgainstThePool() {
        int clusterId = freshCluster();
        clusterStorageQuotaService.setStoragePool(clusterId, 10L);

        clusterStorageQuotaService.setDefaults(
                new ClusterQuotaDefaults(clusterId, 4 * GIB, 3 * GIB, null, null, null, null, null));

        var defaults = clusterStorageQuotaService.findDefaults(clusterId);
        assertEquals(4 * GIB, defaults.quotaBytes(), "a default is not a promise to any one station");
        assertEquals(3 * GIB, defaults.quotaKbBytes());
        assertNull(defaults.quotaBoardBytes());
        assertEquals(0L, clusterStorageQuotaService.findOverview(clusterId).handedOut());
    }

    // -- Tiers --

    @Test
    void aTierIsAddedRenamedAndRemovedWithoutMovingAnybody() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Stufe " + NAMES.incrementAndGet());

        var tier = clusterStorageQuotaService.createPreset(clusterId, "Klein", 2 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);
        clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of(station.uid()));

        clusterStorageQuotaService.updatePreset(
                clusterId, tier.id(), "Mittel", 8 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);
        assertEquals(
                2 * GIB,
                clusterStorageQuotaRepo.findGrant(station.id()).orElseThrow().quotaBytes(),
                "a station keeps what it was given when the tier it came from is edited");

        clusterStorageQuotaService.deletePreset(clusterId, tier.id());
        assertTrue(clusterStorageQuotaService.findPresets(clusterId).isEmpty());
        assertEquals(
                2 * GIB,
                clusterStorageQuotaRepo.findGrant(station.id()).orElseThrow().quotaBytes(),
                "and keeps it when the tier is gone altogether");

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aTierNeedsANameOfItsOwn() {
        int clusterId = freshCluster();
        clusterStorageQuotaService.createPreset(clusterId, "Standard", GIB, GIB, GIB, GIB, GIB, 1024, 1024);

        assertThrows(
                BadRequestResponse.class,
                () -> clusterStorageQuotaService.createPreset(clusterId, "  ", GIB, GIB, GIB, GIB, GIB, 1024, 1024));
        var refused = assertThrows(
                BadRequestResponse.class,
                () -> clusterStorageQuotaService.createPreset(
                        clusterId, "standard", GIB, GIB, GIB, GIB, GIB, 1024, 1024));
        assertTrue(refused.getMessage().contains("already has a tier"));
    }

    @Test
    void aTierBelongsToTheClusterThatMadeIt() {
        int clusterId = freshCluster();
        int otherClusterId = freshCluster();
        var tier = clusterStorageQuotaService.createPreset(
                otherClusterId, "Fremde Stufe", GIB, GIB, GIB, GIB, GIB, 1024, 1024);

        assertThrows(NotFoundResponse.class, () -> clusterStorageQuotaService.deletePreset(clusterId, tier.id()));
        assertThrows(
                NotFoundResponse.class, () -> clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of()));
        assertFalse(clusterStorageQuotaService.findPresets(clusterId).stream()
                .anyMatch(preset -> preset.id() == tier.id()));
    }

    @Test
    void aTierHandedToSeveralStationsIsWeighedOnceForTheLot() {
        int clusterId = freshCluster();
        clusterStorageQuotaService.setStoragePool(clusterId, 5 * GIB);
        var first = clusterService.createStation(clusterId, "Wache Stufe Eins " + NAMES.incrementAndGet());
        var second = clusterService.createStation(clusterId, "Wache Stufe Zwei " + NAMES.incrementAndGet());
        var tier = clusterStorageQuotaService.createPreset(clusterId, "Gross", 3 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);

        var refused = assertThrows(
                BadRequestResponse.class,
                () -> clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of(first.uid(), second.uid())));
        assertTrue(refused.getMessage().contains("more than the cluster has left"));

        clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of(first.uid()));
        var grant = clusterStorageQuotaRepo.findGrant(first.id()).orElseThrow();
        assertEquals(3 * GIB, grant.quotaBytes());
        assertEquals(tier.id(), grant.presetId(), "the station says which tier it is on");
        assertEquals(3 * GIB, clusterStorageQuotaService.findOverview(clusterId).handedOut());

        for (var station : List.of(first, second)) {
            clusterService.releaseStation(clusterId, station.id());
            stationRepo.delete(station.id());
        }
    }

    @Test
    void settingATotalByHandTakesTheStationOffItsTier() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Handarbeit " + NAMES.incrementAndGet());
        var tier = clusterStorageQuotaService.createPreset(clusterId, "Stufe", 2 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);
        clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of(station.uid()));

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 4 * GIB);

        var grant = clusterStorageQuotaRepo.findGrant(station.id()).orElseThrow();
        assertEquals(4 * GIB, grant.quotaBytes());
        assertNull(grant.presetId(), "the numbers no longer come from the tier, so neither does the name");
        assertEquals(
                GIB,
                grant.quotaKbBytes(),
                "the dimensions the screen did not ask about are left exactly where they were");

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    // -- The whole picture --

    @Test
    void theOverviewSaysWhatWasGrantedWhatThatMeansAndWhatIsUsed() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Uebersicht " + NAMES.incrementAndGet());
        clusterStorageQuotaService.setDefaults(
                new ClusterQuotaDefaults(clusterId, 9 * GIB, null, null, null, null, null, null));
        var tier = clusterStorageQuotaService.createPreset(clusterId, "Voll", 6 * GIB, GIB, GIB, GIB, GIB, 1024, 1024);
        clusterStorageQuotaService.applyPreset(clusterId, tier.id(), List.of(station.uid()));

        var overview = clusterStorageQuotaService.findOverview(clusterId);
        var row = overview.stations().stream()
                .filter(entry -> entry.stationUid().equals(station.uid()))
                .findFirst()
                .orElseThrow();

        assertEquals(9 * GIB, overview.defaults().quotaBytes());
        assertEquals(1, overview.presets().size());
        assertEquals(6 * GIB, row.granted().totalBytes(), "what this cluster decided");
        assertEquals(6 * GIB, row.resolved().total().bytes(), "and what the station may therefore keep");
        assertEquals(QuotaOrigin.CLUSTER_GRANT, row.resolved().total().origin());
        assertEquals("Voll", row.presetName());
        assertEquals(0L, row.usedBytes(), "a station that has kept nothing yet");

        var ownStore = overview.stations().stream()
                .filter(ClusterStorageQuotaService.StationRoom::ownStore)
                .findFirst()
                .orElseThrow();
        assertNull(ownStore.granted().totalBytes(), "the cluster's own store was granted nothing of its own");
        assertEquals(
                QuotaOrigin.CLUSTER_DEFAULT,
                ownStore.resolved().total().origin(),
                "so it lives on the cluster's defaults like anybody else");

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }
}
