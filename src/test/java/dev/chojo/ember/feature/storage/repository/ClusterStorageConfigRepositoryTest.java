/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The versions of the storage a cluster keeps, and the stations standing on them.
 */
class ClusterStorageConfigRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterStorageConfigRepository repository;
    private static ClusterStationStorageRepository placements;
    private static CredentialCipher cipher;

    @BeforeAll
    static void setup() {
        repository = new ClusterStorageConfigRepository();
        placements = new ClusterStationStorageRepository();
        cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
    }

    private static StationStorageBackendConfig config(String share) {
        return new StationStorageBackendConfig.SmbVariant(
                "smb.example.invalid",
                445,
                share,
                "WORKGROUP",
                "/base",
                true,
                false,
                cipher.encrypt("{\"username\":\"u\",\"password\":\"p\"}"));
    }

    @Test
    void aClusterWithoutStorageOfItsOwnHasNoCurrentVersion() {
        int clusterId = clusterService
                .create("Kreisverband Ablage " + NAMES.incrementAndGet(), null)
                .id();

        assertTrue(repository.findCurrent(clusterId).isEmpty());
    }

    @Test
    void aNewVersionRetiresTheOneBeforeItAndBothStayReadable() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());

        var first = repository.insertCurrent(cluster.id(), config("erste"));
        assertEquals(
                first.id(), repository.findCurrent(cluster.id()).orElseThrow().id());
        assertEquals(
                cluster.id(),
                repository.findCurrentForStation(station.id()).orElseThrow().clusterId(),
                "a station finds its cluster's storage without knowing the cluster");

        var second = repository.insertCurrent(cluster.id(), config("zweite"));
        assertEquals(
                second.id(), repository.findCurrent(cluster.id()).orElseThrow().id());
        assertFalse(
                repository.findById(first.id()).orElseThrow().current(),
                "the version before it is retired rather than gone, because somebody may be standing on it");
        assertEquals(2, repository.findByCluster(cluster.id()).size());
        assertEquals(
                StorageBackendType.SMB,
                repository.findCurrent(cluster.id()).orElseThrow().config().type());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    /**
     * Rotating a secret must not copy a terabyte, so new credentials for the same destination are written
     * onto the version everybody is already standing on.
     */
    @Test
    void newCredentialsAreWrittenOntoTheVersionRatherThanBesideIt() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var version = repository.insertCurrent(cluster.id(), config("gleich"));

        repository.updateInPlace(version.id(), config("gleich"));

        assertEquals(1, repository.findByCluster(cluster.id()).size(), "and no second version appears");
        var current = repository.findCurrent(cluster.id()).orElseThrow();
        assertEquals(version.id(), current.id());
        assertTrue(current.updatedAt().isAfter(current.createdAt().minusSeconds(1)));

        clusterService.delete(cluster.id());
    }

    /**
     * The foreign key with no {@code ON DELETE} clause is what refuses this, rather than a check somebody has
     * to remember to write.
     */
    @Test
    void aVersionSomebodyStandsOnCannotBeDeleted() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());
        var version = repository.insertCurrent(cluster.id(), config("belegt"));

        placements.place(station.id(), cluster.id(), version.id());
        assertEquals(1, placements.countOn(version.id()));
        assertEquals(1, placements.findByCluster(cluster.id()).size());
        assertEquals(
                version.id(),
                placements.findByStation(station.id()).orElseThrow().configId());
        assertTrue(placements.findConfigForStation(station.id()).isPresent());

        assertThrows(Exception.class, () -> repository.delete(version.id()));

        placements.remove(station.id());
        assertEquals(0, placements.countOn(version.id()));
        repository.delete(version.id());
        assertTrue(repository.findById(version.id()).isEmpty());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    @Test
    void aStationOutsideAnyClusterStandsOnNothing() {
        var station = stationRepo.create("Wache Ohne Verband " + NAMES.incrementAndGet());

        assertTrue(repository.findCurrentForStation(station.id()).isEmpty());
        assertTrue(placements.findByStation(station.id()).isEmpty());

        stationRepo.delete(station.id());
    }
}
