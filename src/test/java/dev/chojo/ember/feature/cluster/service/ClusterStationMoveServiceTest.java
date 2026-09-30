/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.MigrationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClusterStationMoveServiceTest {
    private static final UUID STATION = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Actor ACTOR = Actor.human(1, null);

    private ClusterStorageBackendService backend;
    private StorageBackendAuditService audit;
    private ClusterStationMoveService service;

    @BeforeEach
    void setup() {
        var stations = mock(StationRepository.class);
        var station = mock(Station.class);
        when(station.id()).thenReturn(4);
        when(stations.findByUid(STATION)).thenReturn(Optional.of(station));
        backend = mock(ClusterStorageBackendService.class);
        audit = mock(StorageBackendAuditService.class);
        service = new ClusterStationMoveService(stations, backend, audit);
    }

    @Test
    void aStationsFilesAreCarriedAndTheMoveWrittenDown() {
        when(backend.moveStation(2, 4)).thenReturn(new MigrationResult(5, 4, 1, 0, 64));

        var moved = service.move(ACTOR, 2, STATION.toString());

        assertEquals(4, moved.copied());
        verify(audit).recordMigration(ACTOR, 4, StorageAuditAction.MIGRATION_STARTED, null, null, null);
        verify(audit).recordMigration(ACTOR, 4, StorageAuditAction.MIGRATION_COMPLETED, null, null, null);
    }

    @Test
    void aMoveThatFailsIsRefusedAndWrittenDown() {
        when(backend.moveStation(2, 4)).thenThrow(new MigrationException("target full"));

        var refused = assertThrows(RefusalResponse.class, () -> service.move(ACTOR, 2, STATION.toString()));

        assertEquals(Refusal.CLUSTER_STORAGE_MOVE_FAILED, refused.refusal());
        verify(audit).recordMigration(ACTOR, 4, StorageAuditAction.MIGRATION_FAILED, null, null, "target full");
    }

    @Test
    void aStationThatIsNotOneOrIsNotHereIsRefusedBeforeAnythingMoves() {
        assertEquals(
                Refusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE,
                assertThrows(RefusalResponse.class, () -> service.move(ACTOR, 2, "nord"))
                        .refusal());
        assertEquals(
                Refusal.STATION_NOT_HERE_ON_CLUSTER_STORAGE_MOVE,
                assertThrows(
                                RefusalResponse.class,
                                () -> service.move(ACTOR, 2, UUID.randomUUID().toString()))
                        .refusal());
        verify(backend, never()).moveStation(anyInt(), anyInt());
    }
}
