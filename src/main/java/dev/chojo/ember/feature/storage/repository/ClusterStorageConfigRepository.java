/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.ClusterStorageConfig;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The versions of the storage a cluster keeps, one row each.
 *
 * <p>The same config type as a station's own, deliberately: what a cluster points its stations at is the
 * same kind of thing a station points itself at, and a second variant of the type would only mean the same
 * credentials parsed two ways.
 *
 * <p>What is <em>not</em> here is where any station's bytes are. That is
 * {@link ClusterStationStorageRepository}, and the two are apart because a decision is written in a request
 * and a copy is not.
 */
@Singleton
public class ClusterStorageConfigRepository {
    private static final String COLUMNS = "id, cluster_id, backend_type, config, is_current, created_at, updated_at";

    /**
     * The version the cluster points new placements at.
     *
     * @param clusterId the cluster
     * @return it, or empty when the cluster keeps no storage of its own
     */
    public Optional<ClusterStorageConfig> findCurrent(int clusterId) {
        return query("SELECT %s FROM cluster_storage_config WHERE cluster_id = :cluster_id AND is_current;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStorageConfig.map())
                .first();
    }

    /**
     * One version by its identifier, which is what a placement carries.
     *
     * @param id the version
     * @return it, or empty when it has been deleted
     */
    public Optional<ClusterStorageConfig> findById(int id) {
        return query("SELECT %s FROM cluster_storage_config WHERE id = :id;", COLUMNS)
                .single(call().bind("id", id))
                .map(ClusterStorageConfig.map())
                .first();
    }

    /**
     * Every version a cluster has ever had that has not been deleted, newest first.
     *
     * @param clusterId the cluster
     * @return its versions
     */
    public List<ClusterStorageConfig> findByCluster(int clusterId) {
        return query("SELECT %s FROM cluster_storage_config WHERE cluster_id = :cluster_id ORDER BY id DESC;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStorageConfig.map())
                .all();
    }

    /**
     * Takes the current version out of use without deleting it, so whoever stands on it keeps reaching their
     * bytes.
     *
     * @param clusterId the cluster
     */
    public void retireCurrent(int clusterId) {
        query("UPDATE cluster_storage_config SET is_current = FALSE WHERE cluster_id = :cluster_id AND is_current;")
                .single(call().bind("cluster_id", clusterId))
                .update();
    }

    /**
     * Records a new current version, retiring whichever one was current before it.
     *
     * @param clusterId the cluster
     * @param config    the backend, with its credentials already encrypted by the caller
     * @return the version, which is what a placement will point at
     */
    public ClusterStorageConfig insertCurrent(int clusterId, StationStorageBackendConfig config) {
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
                .orElseThrow();
    }

    /**
     * Writes new credentials onto a version that names the same destination, which moves nobody.
     *
     * @param id     the version
     * @param config the backend, with its credentials already encrypted by the caller
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
     * Deletes a version nobody stands on any more.
     *
     * <p>The foreign key from the placement table refuses the day this is called on one somebody is still
     * standing on, which is the point of it carrying no {@code ON DELETE} clause.
     *
     * @param id the version
     */
    public void delete(int id) {
        query("DELETE FROM cluster_storage_config WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }
}
