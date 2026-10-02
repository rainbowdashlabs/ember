/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.feature.storage.audit.StorageAuditAction;
import dev.chojo.ember.feature.storage.core.BackendRedaction;
import dev.chojo.ember.feature.storage.core.BackendSummary;
import dev.chojo.ember.feature.storage.core.RetiredVersions;
import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.migration.MigrationException;
import dev.chojo.ember.feature.storage.repository.ClusterStationStorageRepository;
import dev.chojo.ember.feature.storage.repository.StationStorageConfigRepository;
import dev.chojo.ember.feature.storage.service.StorageBackendAuditService.Actor;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.Destination;
import dev.chojo.ember.feature.storage.service.StorageMigrationService.MigrationResult;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * The one way a station's files move, whoever moves them: the station itself, its association on demand, or
 * its association when it joins or leaves.
 *
 * <p>The move is written to the history as it happens, from where the files were to where they go, and a
 * version of association storage the station was the last one on is deleted once it has left it. The caller
 * makes its checks first, so the history never says a move started that a check refused.
 */
@Singleton
public class StationMoves {
    private final StorageMigrationService migration;
    private final StationStorageConfigRepository ownConfigs;
    private final ClusterStationStorageRepository placements;
    private final StorageBackendAuditService audit;
    private final RetiredVersions retiredVersions;

    @Inject
    public StationMoves(
            StorageMigrationService migration,
            StationStorageConfigRepository ownConfigs,
            ClusterStationStorageRepository placements,
            StorageBackendAuditService audit,
            RetiredVersions retiredVersions) {
        this.migration = migration;
        this.ownConfigs = ownConfigs;
        this.placements = placements;
        this.audit = audit;
        this.retiredVersions = retiredVersions;
    }

    /**
     * Carries a station's files to a destination and writes the move down.
     *
     * @param actor     who asked
     * @param decidedBy the station itself, or the association that moved it
     * @param stationId the station
     * @param target    where to
     * @return what was carried
     * @throws MigrationException when the move could not be made, after the failure was written down
     */
    public MigrationResult move(Actor actor, Owner decidedBy, int stationId, Destination target) {
        @Nullable BackendSummary before = whereNow(stationId);
        @Nullable BackendSummary after = summaryOf(target);
        Optional<Integer> leftCluster = placements.findByStation(stationId).map(ClusterStationStorage::clusterId);

        audit.recordMove(actor, decidedBy, stationId, StorageAuditAction.MIGRATION_STARTED, before, after, null);
        MigrationResult result;
        try {
            result = migration.moveStation(stationId, target);
        } catch (MigrationException e) {
            audit.recordMove(
                    actor, decidedBy, stationId, StorageAuditAction.MIGRATION_FAILED, before, after, e.getMessage());
            throw e;
        }
        audit.recordMove(actor, decidedBy, stationId, StorageAuditAction.MIGRATION_COMPLETED, before, after, null);
        leftCluster.ifPresent(retiredVersions::sweep);
        return result;
    }

    private @Nullable BackendSummary whereNow(int stationId) {
        return ownConfigs
                .findOne(stationId)
                .map(StationStorageConfigRepository.Row::config)
                .or(() -> placements.findConfigForStation(stationId))
                .map(BackendRedaction::summaryOf)
                .orElse(null);
    }

    private static @Nullable BackendSummary summaryOf(Destination destination) {
        return switch (destination) {
            case Destination.Own own -> BackendRedaction.summaryOf(own.config());
            case Destination.Cluster cluster -> BackendRedaction.summaryOf(cluster.config());
            case Destination.InstanceDefault ignored -> null;
        };
    }
}
