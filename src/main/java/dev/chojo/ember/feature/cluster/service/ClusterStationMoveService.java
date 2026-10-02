/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.core.MigrationResponse;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * An association carrying one of its stations' files to where its decision says they belong, on demand. The
 * move is written to the storage history by the move itself, once every check has passed.
 */
@Singleton
public class ClusterStationMoveService {
    private static final Logger log = LoggerFactory.getLogger(ClusterStationMoveService.class);

    private final StationRepository stationRepository;
    private final ClusterStorageBackendService backendService;

    @Inject
    public ClusterStationMoveService(StationRepository stationRepository, ClusterStorageBackendService backendService) {
        this.stationRepository = stationRepository;
        this.backendService = backendService;
    }

    /**
     * Carries a station's files to where they belong.
     *
     * @param actor      who asked
     * @param clusterId  the association
     * @param stationUid the station, as the address names it
     * @return what was carried
     */
    public MigrationResponse move(Actor actor, int clusterId, String stationUid) {
        int stationId = stationRepository
                .findByUid(parseUid(stationUid))
                .orElseThrow(ClusterRefusal.STATION_NOT_HERE_ON_CLUSTER_STORAGE_MOVE::raise)
                .id();
        StorageMigrationService.MigrationResult result;
        try {
            result = backendService.moveStation(actor, clusterId, stationId);
        } catch (MigrationException e) {
            log.warn("Storage move of station {} by cluster {} failed", stationId, clusterId, e);
            throw ClusterRefusal.CLUSTER_STORAGE_MOVE_FAILED.raise();
        }
        return new MigrationResponse(
                result.totalKeys(), result.copied(), result.skipped(), result.deleted(), result.copiedBytes());
    }

    private static UUID parseUid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw ClusterRefusal.STATION_NOT_AN_IDENTITY_ON_CLUSTER_STORAGE_MOVE.raise();
        }
    }
}
