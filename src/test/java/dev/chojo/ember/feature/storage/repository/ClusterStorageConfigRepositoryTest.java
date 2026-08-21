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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The backend a cluster keeps for its stations.
 */
class ClusterStorageConfigRepositoryTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();

    private static ClusterStorageConfigRepository repository;
    private static CredentialCipher cipher;

    @BeforeAll
    static void setup() {
        repository = new ClusterStorageConfigRepository();
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
    void aClusterWithoutAnOverrideUsesWhateverTheInstanceProvides() {
        int clusterId = clusterService
                .create("Kreisverband Ablage " + NAMES.incrementAndGet(), null)
                .id();

        assertTrue(repository.findOne(clusterId).isEmpty());
    }

    @Test
    void anOverrideIsFoundBothByClusterAndByAnyOfItsStations() {
        var cluster = clusterService.create("Kreisverband Ablage " + NAMES.incrementAndGet(), null);
        var station = clusterService.createStation(cluster.id(), "Wache Ablage " + NAMES.incrementAndGet());

        repository.upsert(cluster.id(), config("erste"));

        assertEquals(
                cluster.id(), repository.findOne(cluster.id()).orElseThrow().clusterId());
        assertEquals(
                cluster.id(),
                repository.findForStation(station.id()).orElseThrow().clusterId(),
                "a station finds its cluster's backend without knowing the cluster");

        // Setting it again replaces rather than duplicating, because a cluster has at most one
        repository.upsert(cluster.id(), config("zweite"));
        assertEquals(
                StorageBackendType.SMB,
                repository.findOne(cluster.id()).orElseThrow().config().type());

        repository.delete(cluster.id());
        assertTrue(repository.findOne(cluster.id()).isEmpty());
        assertTrue(repository.findForStation(station.id()).isEmpty());

        clusterService.releaseStation(cluster.id(), station.id());
        stationRepo.delete(station.id());
        clusterService.delete(cluster.id());
    }

    @Test
    void aStationOutsideAnyClusterFindsNothing() {
        var station = stationRepo.create("Wache Ohne Verband " + NAMES.incrementAndGet());

        assertTrue(repository.findForStation(station.id()).isEmpty());

        stationRepo.delete(station.id());
    }
}
