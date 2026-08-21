/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The backend a cluster keeps for its stations, which sits between a station's own override and the instance
 * default.
 *
 * <p>The same shape as the station's, and deliberately the same config type: what a cluster points its
 * stations at is the same kind of thing a station points itself at, and a second variant of the type would
 * only mean the same credentials parsed two ways.
 */
@Singleton
public class ClusterStorageConfigRepository {

    /**
     * The cluster's override, when it has one.
     *
     * @param clusterId the cluster
     * @return the row, or empty when the cluster uses whatever the instance provides
     */
    public Optional<Row> findOne(int clusterId) {
        return query("""
                SELECT cluster_id, backend_type, config
                FROM cluster_storage_config
                WHERE cluster_id = :cluster_id;
                """)
                .single(call().bind("cluster_id", clusterId))
                .map(row ->
                        new Row(row.getInt("cluster_id"), StationStorageBackendConfig.parse(row.getString("config"))))
                .first();
    }

    /**
     * The cluster's override, found from one of its stations.
     *
     * @param stationId a station of the cluster
     * @return the row, or empty when the station answers to no cluster or the cluster has no override
     */
    public Optional<Row> findForStation(int stationId) {
        return query("""
                SELECT csc.cluster_id, csc.backend_type, csc.config
                FROM cluster_storage_config csc
                JOIN station s ON s.cluster_id = csc.cluster_id
                WHERE s.id = :station_id;
                """)
                .single(call().bind("station_id", stationId))
                .map(row ->
                        new Row(row.getInt("cluster_id"), StationStorageBackendConfig.parse(row.getString("config"))))
                .first();
    }

    /**
     * Insert-or-update the cluster's override.
     *
     * @param clusterId the cluster
     * @param config    the backend, with its credentials already encrypted by the caller
     */
    public void upsert(int clusterId, StationStorageBackendConfig config) {
        query("""
                INSERT INTO cluster_storage_config (cluster_id, backend_type, config, updated_at)
                VALUES (:cluster_id, :backend_type, :config::JSONB, now())
                ON CONFLICT (cluster_id)
                DO UPDATE SET backend_type = excluded.backend_type,
                              config = excluded.config,
                              updated_at = now();
                """)
                .single(call().bind("cluster_id", clusterId)
                        .bind("backend_type", config.type().name())
                        .bind("config", config.toJson()))
                .update();
    }

    /**
     * Removes the cluster's override; no-op when none exists.
     *
     * @param clusterId the cluster
     */
    public void delete(int clusterId) {
        query("DELETE FROM cluster_storage_config WHERE cluster_id = :cluster_id;")
                .single(call().bind("cluster_id", clusterId))
                .delete();
    }

    /**
     * Read-side projection of one row.
     */
    public record Row(int clusterId, StationStorageBackendConfig config) {}
}
