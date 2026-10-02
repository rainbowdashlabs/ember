/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.RetiredVersions;
import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.Destination;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.MigrationResult;
import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Every move of a station's files is written down from where they were to where they went, and the version
 * of association storage the station was the last one on goes once it has left it.
 */
class StationMovesTest {
    private static final int STATION = 4;
    private static final Actor ACTOR = Actor.human(1, null);
    private static final Owner ASSOCIATION = new Owner.Association(2);
    private static final StationStorageBackendConfig ON_CLUSTER =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "", "verband", null);
    private static final StationStorageBackendConfig OWN =
            new StationStorageBackendConfig.SftpVariant("sftp.test", 22, "ember", "", "eigen", null);

    private StorageMigrationService migration;
    private ClusterStationStorageRepository placements;
    private StorageBackendAuditService audit;
    private RetiredVersions retired;
    private StationMoves moves;

    @BeforeEach
    void setup() {
        migration = mock(StorageMigrationService.class);
        placements = mock(ClusterStationStorageRepository.class);
        audit = mock(StorageBackendAuditService.class);
        retired = mock(RetiredVersions.class);
        moves = new StationMoves(migration, mock(StationStorageConfigRepository.class), placements, audit, retired);
        when(placements.findByStation(STATION))
                .thenReturn(Optional.of(new ClusterStationStorage(STATION, 2, 8, Instant.EPOCH)));
        when(placements.findConfigForStation(STATION)).thenReturn(Optional.of(ON_CLUSTER));
    }

    @Test
    void aMoveIsWrittenDownFromWhereTheFilesWereToWhereTheyWent() {
        var target = new Destination.Own(OWN);
        when(migration.moveStation(STATION, target)).thenReturn(new MigrationResult(2, 2, 0, 2, 10));

        var result = moves.move(ACTOR, ASSOCIATION, STATION, target);

        assertEquals(2, result.copied());
        var before = BackendRedaction.summaryOf(ON_CLUSTER);
        var after = BackendRedaction.summaryOf(OWN);
        InOrder order = inOrder(audit, migration, retired);
        order.verify(audit)
                .recordMove(ACTOR, ASSOCIATION, STATION, StorageAuditAction.MIGRATION_STARTED, before, after, null);
        order.verify(migration).moveStation(STATION, target);
        order.verify(audit)
                .recordMove(ACTOR, ASSOCIATION, STATION, StorageAuditAction.MIGRATION_COMPLETED, before, after, null);
        order.verify(retired).sweep(2);
    }

    @Test
    void aMoveThatFailsIsWrittenDownAndLeavesTheVersionsAlone() {
        var home = new Destination.InstanceDefault();
        when(migration.moveStation(STATION, home)).thenThrow(new MigrationException("target full"));

        assertThrows(MigrationException.class, () -> moves.move(ACTOR, ASSOCIATION, STATION, home));

        verify(audit)
                .recordMove(
                        ACTOR,
                        ASSOCIATION,
                        STATION,
                        StorageAuditAction.MIGRATION_FAILED,
                        BackendRedaction.summaryOf(ON_CLUSTER),
                        null,
                        "target full");
        verify(retired, never()).sweep(anyInt());
    }
}
