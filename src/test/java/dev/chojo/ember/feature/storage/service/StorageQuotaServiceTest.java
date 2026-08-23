/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.entity.ClusterQuotaDefaults;
import dev.chojo.ember.feature.storage.entity.ClusterStationQuota;
import dev.chojo.ember.feature.storage.entity.QuotaAuthority;
import dev.chojo.ember.feature.storage.entity.QuotaOrigin;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.repository.ClusterStorageQuotaRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a station may keep, and on whose word.
 *
 * <p>Four parties can have a say and they are consulted in one order: the cluster's grant, the cluster's
 * defaults, the instance's own number for that station, and the instance configuration. The order is the
 * whole subject here, because every number a station is shown comes out of it.
 */
class StorageQuotaServiceTest extends RepositoryTestBase {
    private static final AtomicInteger NAMES = new AtomicInteger();
    private static final long GIB = 1024L * 1024 * 1024;

    private static StorageQuotaService service;
    private static Storage config;
    private static ClusterStorageQuotaRepository quotaRepository;
    private static StationStorageConfigRepository backendRepository;

    @BeforeAll
    static void setup() {
        config = new Storage();
        service = new StorageQuotaService(storageUsageRepo, config, new DomainEventBus(Set.of()));
        quotaRepository = new ClusterStorageQuotaRepository();
        backendRepository = new StationStorageConfigRepository();
    }

    private static int freshCluster() {
        return clusterService
                .create("Kreisverband Speicher " + NAMES.incrementAndGet(), null)
                .id();
    }

    @Test
    void aStationNobodyHasSaidAnythingAboutGetsTheInstanceDefaults() {
        var station = stationRepo.create("Wache Vorgabe " + NAMES.incrementAndGet());

        var quotas = service.resolveQuotas(station.id());

        assertEquals(QuotaAuthority.INSTANCE, quotas.authority());
        assertEquals(config.defaultTotalBytes(), quotas.total().bytes());
        assertEquals(QuotaOrigin.INSTANCE_DEFAULT, quotas.total().origin());
        assertEquals(config.defaultPerFileBytes(), quotas.perFile().bytes());

        stationRepo.delete(station.id());
    }

    @Test
    void aStationUnderNobodyTakesWhatTheInstanceSetForIt() {
        var station = stationRepo.create("Wache Eigenwert " + NAMES.incrementAndGet());

        service.updateStationQuotas(station.id(), 7 * GIB, null, null, null, null, null, null);
        var quotas = service.resolveQuotas(station.id());

        assertEquals(7 * GIB, quotas.total().bytes());
        assertEquals(QuotaOrigin.INSTANCE_OVERRIDE, quotas.total().origin());
        assertEquals(
                QuotaOrigin.INSTANCE_DEFAULT,
                quotas.kb().origin(),
                "the dimensions nobody set still come from the configuration");

        stationRepo.delete(station.id());
    }

    @Test
    void aStationUnderAClusterIsGovernedByItAndNotByTheInstancesOwnNumber() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Verbandsregel " + NAMES.incrementAndGet());

        // The instance says one thing about this station, and the cluster says nothing yet
        service.updateStationQuotas(station.id(), 7 * GIB, null, null, null, null, null, null);

        var underCluster = service.resolveQuotas(station.id());
        assertEquals(
                config.defaultTotalBytes(),
                underCluster.total().bytes(),
                "the instance's lever on a cluster is the pool, not a number on one of its stations");
        assertEquals(QuotaOrigin.INSTANCE_DEFAULT, underCluster.total().origin());

        // And it reaches the station again the moment it answers to nobody
        clusterService.releaseStation(clusterId, station.id());
        var released = service.resolveQuotas(station.id());
        assertEquals(7 * GIB, released.total().bytes());
        assertEquals(QuotaOrigin.INSTANCE_OVERRIDE, released.total().origin());

        stationRepo.delete(station.id());
    }

    @Test
    void theClustersDefaultsStandUntilItGrantsSomethingItself() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Standard " + NAMES.incrementAndGet());
        quotaRepository.setDefaults(new ClusterQuotaDefaults(clusterId, 3 * GIB, null, null, null, null, null, null));

        var onDefaults = service.resolveQuotas(station.id());
        assertEquals(3 * GIB, onDefaults.total().bytes());
        assertEquals(QuotaOrigin.CLUSTER_DEFAULT, onDefaults.total().origin());
        assertEquals(
                QuotaOrigin.INSTANCE_DEFAULT,
                onDefaults.kb().origin(),
                "a dimension the cluster left alone falls through to the instance");

        clusterStorageQuotaService.setTotal(clusterId, station.id(), 5 * GIB);

        var granted = service.resolveQuotas(station.id());
        assertEquals(5 * GIB, granted.total().bytes());
        assertEquals(QuotaOrigin.CLUSTER_GRANT, granted.total().origin());

        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aReleasedStationKeepsNothingTheClusterGaveIt() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Entlassen " + NAMES.incrementAndGet());
        quotaRepository.setDefaults(new ClusterQuotaDefaults(clusterId, 3 * GIB, null, null, null, null, null, null));
        clusterStorageQuotaService.setTotal(clusterId, station.id(), 5 * GIB);

        clusterService.releaseStation(clusterId, station.id());

        var quotas = service.resolveQuotas(station.id());
        assertEquals(config.defaultTotalBytes(), quotas.total().bytes());
        assertEquals(QuotaOrigin.INSTANCE_DEFAULT, quotas.total().origin());

        stationRepo.delete(station.id());
    }

    @Test
    void theClustersOwnStoreIsGovernedByTheClusterToo() {
        int clusterId = freshCluster();
        var cluster = clusterRepo.findById(clusterId).orElseThrow();
        quotaRepository.setDefaults(new ClusterQuotaDefaults(clusterId, 2 * GIB, null, null, null, null, null, null));

        var quotas = service.resolveQuotas(cluster.homeStationId());

        assertEquals(
                2 * GIB,
                quotas.total().bytes(),
                "the files a cluster keeps are kept on the station it owns, and are no more free than anybody else's");
        assertEquals(QuotaOrigin.CLUSTER_DEFAULT, quotas.total().origin());
    }

    @Test
    void aStationPayingForItsOwnStorageIsBoundedByNobody() {
        var station = stationRepo.create("Wache Eigener Speicher " + NAMES.incrementAndGet());
        service.updateStationQuotas(station.id(), GIB, null, null, null, null, null, null);
        backendRepository.upsert(station.id(), backend("eigen"));

        var quotas = service.resolveQuotas(station.id());

        assertEquals(QuotaAuthority.NOBODY, quotas.authority());
        assertEquals(QuotaOrigin.UNLIMITED, quotas.total().origin());
        assertEquals(Long.MAX_VALUE, quotas.perImage().bytes());

        backendRepository.delete(station.id());
        stationRepo.delete(station.id());
    }

    @Test
    void aClusterPayingForItsStationsBindsThemAndTheInstanceNoLongerDoes() {
        int clusterId = freshCluster();
        var station = clusterService.createStation(clusterId, "Wache Verbandsspeicher " + NAMES.incrementAndGet());
        clusterGovernanceService.setStorageBackend(clusterId, backend("verband"));
        quotaRepository.setGrant(
                new ClusterStationQuota(station.id(), clusterId, 6 * GIB, null, null, null, null, null, null, null));

        var quotas = service.resolveQuotas(station.id());

        assertEquals(QuotaAuthority.CLUSTER, quotas.authority());
        assertEquals(6 * GIB, quotas.total().bytes());
        assertEquals(QuotaOrigin.CLUSTER_GRANT, quotas.total().origin());
        assertEquals(
                QuotaOrigin.UNLIMITED,
                quotas.kb().origin(),
                "whoever pays sets the limit, and the cluster set none here");

        clusterGovernanceService.setStorageBackend(clusterId, null);
        clusterService.releaseStation(clusterId, station.id());
        stationRepo.delete(station.id());
    }

    private static StationStorageBackendConfig backend(String share) {
        var cipher = new CredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
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
}
