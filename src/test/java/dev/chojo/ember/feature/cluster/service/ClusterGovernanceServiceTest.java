/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a cluster decides for its stations, and what it may not decide.
 *
 * <p>Room is decided elsewhere: {@link ClusterStorageQuotaService} hands out portions of a pool, which is
 * arithmetic rather than a rule, and {@code ClusterStorageQuotaServiceTest} walks it.
 */
class ClusterGovernanceServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private int freshCluster() {
        return clusterService
                .create("Kreisverband Regie " + NAMES.incrementAndGet(), null)
                .id();
    }

    @Test
    void aDeniedModuleIsOffAtEveryStationWhateverTheStationSaid() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Modulfrei " + NAMES.incrementAndGet());

        clusterGovernanceService.setDeniedModules(clusterId, null, Set.of(StationModule.QUIZ));

        assertFalse(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.EVENTS));
        assertTrue(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.QUIZ));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    /**
     * The whole point of keying a denial to a filing: not every station of an association does the same
     * work, so not every station has to lose the same module.
     */
    @Test
    void aModuleDeniedForOneGroupIsDeniedThereAndNowhereElse() {
        int clusterId = freshCluster();
        var inside = clusterService.createStation(clusterId, "Wache Innen " + NAMES.incrementAndGet());
        var outside = clusterService.createStation(clusterId, "Wache Aussen " + NAMES.incrementAndGet());
        var group = clusterStationGroupService.create(clusterId, "Ohne Quiz " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, group.id(), List.of(inside.uid()));

        clusterGovernanceService.setDeniedModules(clusterId, group.id(), Set.of(StationModule.QUIZ));

        assertTrue(clusterRepo.isModuleDeniedForStation(inside.id(), StationModule.QUIZ));
        assertFalse(
                clusterRepo.isModuleDeniedForStation(outside.id(), StationModule.QUIZ),
                "a station outside the filing keeps it");
        assertTrue(
                clusterGovernanceService.findDeniedModules(clusterId, null).isEmpty(),
                "and nothing was denied of everybody");

        // Taking the station out of the group gives it back, without anybody touching the denial
        clusterStationGroupService.setStations(clusterId, group.id(), List.of());
        assertFalse(clusterRepo.isModuleDeniedForStation(inside.id(), StationModule.QUIZ));

        clusterStationGroupService.setStations(clusterId, group.id(), List.of(inside.uid()));
        clusterGovernanceService.setDeniedModules(clusterId, group.id(), EnumSet.noneOf(StationModule.class));
        clusterStationGroupService.delete(clusterId, group.id());
        clusterService.releaseStation(clusterId, inside.id());
        clusterService.releaseStation(clusterId, outside.id());
        stationRepo.delete(inside.id());
        stationRepo.delete(outside.id());
    }

    /** Denials add up. Neither way of writing one can give back what the other took. */
    @Test
    void aDenialForEverybodyAndOneForAGroupAreTheUnion() {
        int clusterId = freshCluster();
        var inside = clusterService.createStation(clusterId, "Wache Beides " + NAMES.incrementAndGet());
        var group = clusterStationGroupService.create(clusterId, "Ohne Quiz " + NAMES.incrementAndGet());
        clusterStationGroupService.setStations(clusterId, group.id(), List.of(inside.uid()));

        clusterGovernanceService.setDeniedModules(clusterId, null, Set.of(StationModule.EVENTS));
        clusterGovernanceService.setDeniedModules(clusterId, group.id(), Set.of(StationModule.QUIZ));

        assertTrue(clusterRepo.isModuleDeniedForStation(inside.id(), StationModule.EVENTS));
        assertTrue(clusterRepo.isModuleDeniedForStation(inside.id(), StationModule.QUIZ));
        assertEquals(
                Set.of(StationModule.EVENTS, StationModule.QUIZ),
                clusterRepo.findDeniedModulesForStation(inside.id()),
                "the station is told both, from wherever they were written");

        // And saving one tab leaves the other exactly as it was
        clusterGovernanceService.setDeniedModules(clusterId, null, EnumSet.noneOf(StationModule.class));
        assertEquals(Set.of(StationModule.QUIZ), clusterGovernanceService.findDeniedModules(clusterId, group.id()));

        clusterGovernanceService.setDeniedModules(clusterId, group.id(), EnumSet.noneOf(StationModule.class));
        clusterStationGroupService.delete(clusterId, group.id());
        clusterService.releaseStation(clusterId, inside.id());
        stationRepo.delete(inside.id());
    }

    /**
     * Dropping a filing that modules are switched off for would switch them back on at every station in
     * it, without anybody deciding that. Refused, saying how many are in the way.
     */
    @Test
    void aGroupModulesAreDeniedForCannotBeRemoved() {
        int clusterId = freshCluster();
        var group = clusterStationGroupService.create(clusterId, "Ohne Quiz " + NAMES.incrementAndGet());
        clusterGovernanceService.setDeniedModules(clusterId, group.id(), Set.of(StationModule.QUIZ));

        var refused =
                assertThrows(BadRequestResponse.class, () -> clusterStationGroupService.delete(clusterId, group.id()));
        assertTrue(refused.getMessage().contains("1 module"));

        clusterGovernanceService.setDeniedModules(clusterId, group.id(), EnumSet.noneOf(StationModule.class));
        clusterStationGroupService.delete(clusterId, group.id());
        assertTrue(clusterStationGroupService.findByCluster(clusterId).isEmpty());
    }

    /** A filing of another association is not this one's to decide for. */
    @Test
    void oneAssociationCannotDenyThroughAnothersGroup() {
        int clusterId = freshCluster();
        int otherId = freshCluster();
        var group = clusterStationGroupService.create(otherId, "Fremd " + NAMES.incrementAndGet());

        assertThrows(
                BadRequestResponse.class,
                () -> clusterGovernanceService.setDeniedModules(clusterId, group.id(), Set.of(StationModule.QUIZ)));
        assertThrows(BadRequestResponse.class, () -> clusterGovernanceService.findDeniedModules(clusterId, group.id()));

        clusterStationGroupService.delete(otherId, group.id());
    }

    @Test
    void liftingADenialGivesTheModuleBack() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Zurueck " + NAMES.incrementAndGet());

        clusterGovernanceService.setDeniedModules(clusterId, null, Set.of(StationModule.QUIZ));
        clusterGovernanceService.setDeniedModules(clusterId, null, EnumSet.noneOf(StationModule.class));

        assertFalse(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.QUIZ));
        assertTrue(clusterGovernanceService.findDeniedModules(clusterId, null).isEmpty());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aReleasedStationKeepsNoneOfTheClustersDenials() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Frei " + NAMES.incrementAndGet());
        clusterGovernanceService.setDeniedModules(clusterId, null, Set.of(StationModule.QUIZ));

        clusterService.releaseStation(clusterId, station.id());

        assertFalse(
                clusterRepo.isModuleDeniedForStation(station.id(), StationModule.QUIZ),
                "the denial went with the membership");
        stationRepo.delete(station.id());
    }

    @Test
    void theClustersLookIsWrittenToItsStations() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Bunt " + NAMES.incrementAndGet());

        clusterGovernanceService.setLookAndFeel(
                clusterId, "midnight", null, ThemeFeel.CORNERS, true, false, true, false);

        var updated = stationRepo.findById(station.id()).orElseThrow();
        assertEquals("midnight", updated.defaultTheme());
        assertEquals(ThemeFeel.CORNERS, updated.defaultFeel());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aSettingTheClusterHasNoOpinionOnLeavesTheStationsOwn() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Eigen " + NAMES.incrementAndGet());
        String before = stationRepo.findById(station.id()).orElseThrow().defaultTheme();

        clusterGovernanceService.setLookAndFeel(clusterId, null, null, null, false, false, false, false);

        assertEquals(before, stationRepo.findById(station.id()).orElseThrow().defaultTheme());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aStationJoiningIsHandedTheLookAtOnce() {
        int clusterId = freshCluster();
        clusterGovernanceService.setLookAndFeel(clusterId, "midnight", null, null, true, false, false, false);
        var station = stationRepo.create("Wache Beitritt " + NAMES.incrementAndGet());

        clusterService.joinStation(clusterId, station.id());

        assertEquals(
                "midnight", stationRepo.findById(station.id()).orElseThrow().defaultTheme());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aClusterThatIsNotThereGovernsNothing() {
        assertThrows(
                NotFoundResponse.class,
                () -> clusterGovernanceService.setDeniedModules(999_999, null, Set.of(StationModule.QUIZ)));
    }
}
