/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.EnumSet;
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

        clusterGovernanceService.setDeniedModules(clusterId, Set.of(StationModule.QUIZ));

        assertFalse(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.EVENTS));
        assertTrue(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.QUIZ));

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void liftingADenialGivesTheModuleBack() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Zurueck " + NAMES.incrementAndGet());

        clusterGovernanceService.setDeniedModules(clusterId, Set.of(StationModule.QUIZ));
        clusterGovernanceService.setDeniedModules(clusterId, EnumSet.noneOf(StationModule.class));

        assertFalse(clusterRepo.isModuleDeniedForStation(station.id(), StationModule.QUIZ));
        assertTrue(clusterGovernanceService.findDeniedModules(clusterId).isEmpty());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aReleasedStationKeepsNoneOfTheClustersDenials() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Frei " + NAMES.incrementAndGet());
        clusterGovernanceService.setDeniedModules(clusterId, Set.of(StationModule.QUIZ));

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
    void aClusterCanPointItsStationsAtABackendOfItsOwn() {
        int clusterId = freshCluster();
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        var backend = new StationStorageBackendConfig.SmbVariant(
                "smb.example.invalid",
                445,
                "verband",
                "WORKGROUP",
                "/base",
                true,
                false,
                cipher.encrypt("{\"username\":\"u\",\"password\":\"p\"}"));

        assertTrue(clusterGovernanceService.findStorageBackend(clusterId).isEmpty());

        clusterGovernanceService.setStorageBackend(clusterId, backend);
        assertEquals(
                StorageBackendType.SMB,
                clusterGovernanceService
                        .findStorageBackend(clusterId)
                        .orElseThrow()
                        .type());

        // Passing nothing clears it again, which puts the stations back on the instance default
        clusterGovernanceService.setStorageBackend(clusterId, null);
        assertTrue(clusterGovernanceService.findStorageBackend(clusterId).isEmpty());
    }

    @Test
    void aClusterThatIsNotThereGovernsNothing() {
        assertThrows(
                NotFoundResponse.class,
                () -> clusterGovernanceService.setDeniedModules(999_999, Set.of(StationModule.QUIZ)));
        assertThrows(NotFoundResponse.class, () -> clusterGovernanceService.setStorageBackend(999_999, null));
    }
}
