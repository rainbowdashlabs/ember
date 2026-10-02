/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The versions of the storage a cluster keeps, one row each, in the same config type as a station's own.
 * Where a station's bytes actually are is {@link ClusterStationStorageRepository}: a decision is written
 * in a request and the copy only later.
 */
@Singleton
public class ClusterStorageConfigRepository {
    private static final String COLUMNS = "id, cluster_id, backend_type, config, is_current, created_at, updated_at";

    /** The version the cluster points new placements at; empty when it keeps no storage of its own. */
    public Optional<ClusterStorageConfig> findCurrent(int clusterId) {
        return query("SELECT %s FROM cluster_storage_config WHERE cluster_id = :cluster_id AND is_current;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStorageConfig.map())
                .first();
    }

    /** One version by the identifier a placement carries; empty when it was deleted. */
    public Optional<ClusterStorageConfig> findById(int id) {
        return query("SELECT %s FROM cluster_storage_config WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(ClusterStorageConfig.map())
                .first();
    }

    /** Every version of a cluster that has not been deleted, newest first. */
    public List<ClusterStorageConfig> findByCluster(int clusterId) {
        return query("SELECT %s FROM cluster_storage_config WHERE cluster_id = :cluster_id ORDER BY id DESC;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStorageConfig.map())
                .all();
    }

    /** Takes the current version out of use without deleting it, so whoever stands on it keeps their bytes. */
    public void retireCurrent(int clusterId) {
        query("UPDATE cluster_storage_config SET is_current = FALSE WHERE cluster_id = :cluster_id AND is_current;")
                .single(call().bind("cluster_id", clusterId))
                .update();
    }

    /**
     * Records a new current version, retiring the one before it, as one change: a version that cannot be
     * written leaves the one before it current.
     *
     * @param config the backend, its credentials encrypted by the caller
     */
    public ClusterStorageConfig insertCurrent(int clusterId, StationStorageBackendConfig config) {
        return Transactions.call(() -> {
            retireCurrent(clusterId);
            return query("""
                    INSERT INTO cluster_storage_config (cluster_id, backend_type, config, is_current)
                    VALUES (:cluster_id, :backend_type, :config::JSONB, TRUE)
                    RETURNING %s;""", COLUMNS)
                    .single(call().bind("cluster_id", clusterId)
                            .bind("backend_type", config.type().name())
                            .bind("config", config.toJson()))
                    .map(ClusterStorageConfig.map())
                    .first()
                    .orElseThrow(() -> new IllegalStateException(
                            "A storage version of cluster " + clusterId + " could not be written"));
        });
    }

    /**
     * Writes new credentials onto a version that names the same destination, which moves nobody.
     *
     * @param config the backend, its credentials encrypted by the caller
     */
    public void updateInPlace(int id, StationStorageBackendConfig config) {
        query("""
                UPDATE cluster_storage_config
                SET backend_type = :backend_type, config = :config::JSONB, updated_at = now()
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("backend_type", config.type().name())
                        .bind("config", config.toJson()))
                .update();
    }

    /**
     * Deletes every retired version of a cluster nobody stands on any more, and its credentials with it.
     *
     * <p>The current version stays even with nobody on it, since it is where the next station is carried. The
     * placement table's foreign key, with no {@code ON DELETE} clause, still refuses a version somebody is
     * being carried onto at the same moment.
     *
     * @param clusterId the cluster
     * @return the versions deleted
     */
    public List<Integer> deleteRetiredUnused(int clusterId) {
        return query("""
                DELETE FROM cluster_storage_config csc
                WHERE csc.cluster_id = :cluster_id
                  AND NOT csc.is_current
                  AND NOT EXISTS (SELECT 1 FROM cluster_station_storage css WHERE css.config_id = csc.id)
                RETURNING csc.id;""")
                .single(call().bind("cluster_id", clusterId))
                .map(row -> row.getInt("id"))
                .all();
    }
}
