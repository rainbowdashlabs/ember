/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.audit.StorageAuditOutcome;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ClusterRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.LocalRequest;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.ProbeResult;
import dev.chojo.ember.feature.storage.service.StorageBackendPayloads.SftpSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StationStorageBackendServiceTest {
    private static final int STATION = 4;
    private static final Actor ACTOR = Actor.human(1, 11);
    private static final StationStorageBackendConfig OWN =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "SHA256:x", "files", null);

    private StationStorageConfigRepository configs;
    private StationRepository stations;
    private ClusterRepository clusters;
    private ClusterStorageConfigRepository clusterConfigs;
    private StorageProbeService probes;
    private StorageMigrationService migration;
    private StorageBackendAuditService audit;
    private StationStorageBackendService service;

    @BeforeEach
    void setup() {
        configs = mock(StationStorageConfigRepository.class);
        stations = mock(StationRepository.class);
        clusters = mock(ClusterRepository.class);
        clusterConfigs = mock(ClusterStorageConfigRepository.class);
        probes = mock(StorageProbeService.class);
        migration = mock(StorageMigrationService.class);
        audit = mock(StorageBackendAuditService.class);
        var resolver = mock(StorageBackendResolver.class);
        var instance = mock(StorageBackend.class);
        when(instance.type()).thenReturn(StorageBackendType.LOCAL);
        when(resolver.instanceDefault()).thenReturn(instance);
        service = new StationStorageBackendService(
                configs,
                stations,
                clusters,
                clusterConfigs,
                mock(ClusterStationStorageRepository.class),
                resolver,
                mock(StorageBackendPayloads.class),
                probes,
                migration,
                audit);
    }

    private Cluster cluster(boolean locked, ClusterBackendReach reach) {
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(2);
        when(cluster.name()).thenReturn("Kreisverband");
        when(cluster.storageBackendLocked()).thenReturn(locked);
        when(cluster.storageBackendReach()).thenReturn(reach);
        when(clusters.findByStation(STATION)).thenReturn(Optional.of(cluster));
        return cluster;
    }

    @Test
    void aSessionWithoutAStationOrWithOneThatIsGoneIsRefused() {
        assertEquals(
                Refusal.NO_STATION_CHOSEN_FOR_STORAGE,
                assertThrows(RefusalResponse.class, () -> service.requireStation(null))
                        .refusal());
        assertEquals(
                Refusal.STORAGE_STATION_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.requireStation(STATION))
                        .refusal());

        when(stations.findById(STATION)).thenReturn(Optional.of(mock(Station.class)));
        assertEquals(STATION, service.requireStation(STATION));
    }

    @Test
    void theDescriptionSaysWhatIsBehindTheStationAndOnWhoseWord() {
        cluster(true, ClusterBackendReach.EVERY_STATION);
        when(configs.findOne(STATION)).thenReturn(Optional.of(new StationStorageConfigRepository.Row(STATION, OWN)));
        when(clusterConfigs.findCurrent(2))
                .thenReturn(Optional.of(new ClusterStorageConfig(8, 2, OWN, true, Instant.EPOCH, Instant.EPOCH)));

        var description = service.describe(STATION);

        assertEquals(StorageBackendType.LOCAL, description.instanceDefault());
        assertEquals(new SftpSummary("sftp.test", 22, "ember", true, "files"), description.override());
        assertEquals("Kreisverband", description.clusterName());
        assertTrue(description.clusterOffersStorage());
        assertTrue(description.locked());
    }

    @Test
    void aLockedAssociationKeepsItsStationsFromChoosing() {
        cluster(true, ClusterBackendReach.EVERY_STATION);

        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new LocalRequest()));

        assertEquals(Refusal.ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT, refused.refusal());
        verify(migration, never()).moveStation(anyInt(), any());
    }

    @Test
    void aStationCannotMoveOntoAnAssociationItDoesNotBelongTo() {
        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new ClusterRequest()));

        assertEquals(Refusal.STATION_ANSWERS_TO_NO_ASSOCIATION, refused.refusal());
    }

    @Test
    void anAssociationThatKeepsNoStorageForItsStationsCannotBeMovedOnto() {
        cluster(false, ClusterBackendReach.OWN_FILES);

        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new ClusterRequest()));

        assertEquals(Refusal.ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS, refused.refusal());
    }

    @Test
    void aStationMovesOntoItsAssociationsCurrentStorage() {
        cluster(false, ClusterBackendReach.EVERY_STATION);
        when(clusterConfigs.findCurrent(2))
                .thenReturn(Optional.of(new ClusterStorageConfig(8, 2, OWN, true, Instant.EPOCH, Instant.EPOCH)));
        when(migration.moveStation(eq(STATION), any()))
                .thenReturn(new StorageMigrationService.MigrationResult(4, 3, 1, 0, 99));

        var moved = service.apply(ACTOR, STATION, new ClusterRequest());

        assertEquals(3, moved.copied());
        verify(migration).moveStation(STATION, new StorageMigrationService.Destination.Cluster(2, 8, OWN));
        verify(audit).recordMigration(ACTOR, STATION, StorageAuditAction.MIGRATION_COMPLETED, null, null, null);
    }

    @Test
    void aMoveThatFailsIsRefusedAndWrittenDown() {
        when(migration.moveStation(eq(STATION), any())).thenThrow(new MigrationException("connection refused"));

        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new LocalRequest()));

        assertEquals(Refusal.STATION_STORAGE_MOVE_NOT_DONE, refused.refusal());
        verify(audit)
                .recordMigration(ACTOR, STATION, StorageAuditAction.MIGRATION_FAILED, null, null, "connection refused");
    }

    /**
     * The probe opens a connection to an address the station named, so the reason it failed would
     * say what answers there. It goes to the log and the history, and the answer says where to look.
     */
    @Test
    void aFailedProbeKeepsItsReasonOutOfTheAnswer() {
        when(configs.findOne(STATION)).thenReturn(Optional.of(new StationStorageConfigRepository.Row(STATION, OWN)));
        when(probes.probe(OWN)).thenReturn(new ProbeResult(false, "Connection refused", "2026-09-01T10:00:00Z"));

        var result = service.probe(ACTOR, STATION);

        assertFalse(result.healthy());
        assertEquals(StationStorageBackendService.PROBE_FAILED, result.error());
        verify(audit)
                .recordProbe(ACTOR, STATION, StorageAuditOutcome.FAILED, StationStorageBackendService.PROBE_FAILED);
    }

    @Test
    void aStationWithoutStorageOfItsOwnHasNothingToProbe() {
        var refused = assertThrows(RefusalResponse.class, () -> service.probe(ACTOR, STATION));

        assertEquals(Refusal.STATION_KEEPS_NO_STORAGE_OF_ITS_OWN, refused.refusal());
        verify(audit, never()).recordProbe(any(), anyInt(), any(), isNull());
    }
}
