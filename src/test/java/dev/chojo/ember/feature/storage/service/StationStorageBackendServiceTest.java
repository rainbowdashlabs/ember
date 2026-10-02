/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.StorageRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterBackendReach;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.backend.StorageBackend;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.StorageBackendType;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendRequest.ClusterStorageRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.LocalRequest;
import dev.chojo.ember.feature.storage.core.BackendRequest.SftpRequest;
import dev.chojo.ember.feature.storage.core.BackendSummary.SftpSummary;
import dev.chojo.ember.feature.storage.core.BackendValidation;
import dev.chojo.ember.feature.storage.core.ProbeResult;
import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.ClusterStorageConfigRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.Destination;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    private static final Owner OWNER = new Owner.Station(STATION);
    private static final Actor ACTOR = Actor.human(1, 11);
    private static final StationStorageBackendConfig OWN =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "SHA256:x", "files", null);
    private static final SftpRequest TYPED_IN =
            new SftpRequest("sftp.test", 22, "ember", "SHA256:x", "files", "secret", "");

    private StationStorageConfigRepository configs;
    private StationRepository stations;
    private ClusterRepository clusters;
    private ClusterStorageConfigRepository clusterConfigs;
    private BackendValidation validation;
    private StorageProbeService probes;
    private StationMoves moves;
    private StorageBackendAuditService audit;
    private StationStorageBackendService service;

    @BeforeEach
    void setup() {
        configs = mock(StationStorageConfigRepository.class);
        stations = mock(StationRepository.class);
        clusters = mock(ClusterRepository.class);
        clusterConfigs = mock(ClusterStorageConfigRepository.class);
        validation = mock(BackendValidation.class);
        probes = mock(StorageProbeService.class);
        moves = mock(StationMoves.class);
        audit = mock(StorageBackendAuditService.class);
        var resolver = mock(StorageBackendResolver.class);
        var instance = mock(StorageBackend.class);
        when(instance.type()).thenReturn(StorageBackendType.LOCAL);
        when(resolver.instanceDefault()).thenReturn(instance);
        when(moves.move(any(), any(), anyInt(), any()))
                .thenReturn(new StorageMigrationService.MigrationResult(4, 3, 1, 0, 99));
        service = new StationStorageBackendService(
                configs,
                stations,
                clusters,
                clusterConfigs,
                mock(ClusterStationStorageRepository.class),
                resolver,
                validation,
                probes,
                moves,
                audit);
    }

    private void cluster(boolean locked, ClusterBackendReach reach) {
        var cluster = mock(Cluster.class);
        when(cluster.id()).thenReturn(2);
        when(cluster.name()).thenReturn("Kreisverband");
        when(cluster.storageBackendLocked()).thenReturn(locked);
        when(cluster.storageBackendReach()).thenReturn(reach);
        when(clusters.findByStation(STATION)).thenReturn(Optional.of(cluster));
    }

    private void bringsItsOwn() {
        when(configs.findOne(STATION)).thenReturn(Optional.of(new StationStorageConfigRepository.Row(STATION, OWN)));
    }

    @Test
    void aSessionWithAStationThatIsGoneIsRefused() {
        assertEquals(
                StorageRefusal.STORAGE_STATION_NOT_HERE,
                assertThrows(RefusalResponse.class, () -> service.requireStation(STATION))
                        .refusal());

        when(stations.findById(STATION)).thenReturn(Optional.of(mock(Station.class)));
        assertEquals(STATION, service.requireStation(STATION));
    }

    @Test
    void theDescriptionSaysWhatIsBehindTheStationAndOnWhoseWord() {
        cluster(true, ClusterBackendReach.EVERY_STATION);
        bringsItsOwn();
        when(clusterConfigs.findCurrent(2))
                .thenReturn(Optional.of(new ClusterStorageConfig(8, 2, OWN, true, Instant.EPOCH, Instant.EPOCH)));

        var description = service.describe(STATION);

        assertEquals(StorageBackendType.LOCAL, description.instanceDefault());
        assertEquals(new SftpSummary("sftp.test", 22, "ember", true, "files", null), description.override());
        assertEquals("Kreisverband", description.clusterName());
        assertTrue(description.clusterOffersStorage());
        assertTrue(description.locked());
    }

    @Test
    void aLockedAssociationKeepsItsStationsFromChoosingAndTheRefusalIsWrittenDown() {
        cluster(true, ClusterBackendReach.EVERY_STATION);

        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new LocalRequest(null)));

        assertEquals(StorageRefusal.ASSOCIATION_DECIDES_WHERE_FILES_ARE_KEPT, refused.refusal());
        verify(moves, never()).move(any(), any(), anyInt(), any());
        verify(audit).recordRejected(ACTOR, OWNER, refused);
    }

    @Test
    void storageThatIsNotUsableIsRefusedBeforeAnythingMoves() {
        var incomplete = StorageRefusal.STORAGE_SIGN_IN_AMBIGUOUS.raise();
        when(validation.toConfig(TYPED_IN)).thenThrow(incomplete);

        assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, TYPED_IN));

        verify(moves, never()).move(any(), any(), anyInt(), any());
        verify(audit).recordRejected(ACTOR, OWNER, incomplete);
    }

    @Test
    void aStationCannotMoveOntoAnAssociationItDoesNotBelongTo() {
        var refused =
                assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new ClusterStorageRequest()));

        assertEquals(StorageRefusal.STATION_ANSWERS_TO_NO_ASSOCIATION, refused.refusal());
    }

    @Test
    void anAssociationThatKeepsNoStorageForItsStationsCannotBeMovedOnto() {
        cluster(false, ClusterBackendReach.OWN_FILES);

        var refused =
                assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new ClusterStorageRequest()));

        assertEquals(StorageRefusal.ASSOCIATION_KEEPS_NO_STORAGE_FOR_STATIONS, refused.refusal());
    }

    @Test
    void aStationMovesOntoItsAssociationsCurrentStorage() {
        cluster(false, ClusterBackendReach.EVERY_STATION);
        when(clusterConfigs.findCurrent(2))
                .thenReturn(Optional.of(new ClusterStorageConfig(8, 2, OWN, true, Instant.EPOCH, Instant.EPOCH)));

        var moved = service.apply(ACTOR, STATION, new ClusterStorageRequest());

        assertEquals(3, moved.copied());
        verify(moves).move(ACTOR, OWNER, STATION, new Destination.Cluster(2, 8, OWN));
        verify(audit, never()).recordConfigChange(any(), any(), any(), any(), any());
    }

    /** Storage of its own set for the first time, replaced, and given up are each written down as such. */
    @Test
    void whatAMoveDidToTheStationsOwnStorageIsWrittenDown() {
        when(validation.toConfig(TYPED_IN)).thenReturn(OWN);
        var summary = BackendRedaction.summaryOf(OWN);

        service.apply(ACTOR, STATION, TYPED_IN);
        verify(audit).recordConfigChange(ACTOR, OWNER, StorageAuditAction.CREATED, null, summary);

        bringsItsOwn();
        service.apply(ACTOR, STATION, TYPED_IN);
        verify(audit).recordConfigChange(ACTOR, OWNER, StorageAuditAction.UPDATED, summary, summary);

        service.apply(ACTOR, STATION, new LocalRequest(null));
        verify(audit).recordConfigChange(ACTOR, OWNER, StorageAuditAction.DELETED, summary, null);
        verify(moves).move(ACTOR, OWNER, STATION, new Destination.InstanceDefault());
    }

    @Test
    void aMoveThatFailsIsRefusedWithoutItsReason() {
        when(moves.move(any(), any(), eq(STATION), any())).thenThrow(new MigrationException("connection refused"));

        var refused = assertThrows(RefusalResponse.class, () -> service.apply(ACTOR, STATION, new LocalRequest(null)));

        assertEquals(StorageRefusal.STATION_STORAGE_MOVE_NOT_DONE, refused.refusal());
        verify(audit, never()).recordConfigChange(any(), any(), any(), any(), any());
    }

    @Test
    void theSavedStorageIsProbedForTheStationsHistory() {
        bringsItsOwn();
        var answer = new ProbeResult(false, StorageProbeService.PROBE_FAILED, "2026-09-01T10:00:00Z");
        when(probes.probeSaved(ACTOR, OWNER, OWN)).thenReturn(answer);

        assertEquals(answer, service.probe(ACTOR, STATION));
    }

    @Test
    void storageNotSavedYetIsCheckedBeforeItIsProbed() {
        var answer = new ProbeResult(true, null, "2026-09-01T10:00:00Z");
        when(validation.toConfig(TYPED_IN)).thenReturn(OWN);
        when(probes.probe(OWNER, OWN)).thenReturn(answer);

        assertEquals(answer, service.probe(STATION, TYPED_IN));
    }

    @Test
    void aStationWithoutStorageOfItsOwnHasNothingToProbe() {
        var refused = assertThrows(RefusalResponse.class, () -> service.probe(ACTOR, STATION));

        assertEquals(StorageRefusal.STATION_KEEPS_NO_STORAGE_OF_ITS_OWN, refused.refusal());
        verify(probes, never()).probeSaved(any(), any(), isNull());
    }
}
