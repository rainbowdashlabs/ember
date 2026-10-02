/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.ClusterStationStorage;
import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Where the stations standing on a cluster's storage actually are. A row is a copy that finished, never
 * a decision, which is the difference to the policy on the cluster row.
 */
@Singleton
public class ClusterStationStorageRepository {
    private static final String COLUMNS = "station_id, cluster_id, config_id, moved_at";

    /** Where one station's bytes are; empty when they are on its own backend or the instance default. */
    public Optional<ClusterStationStorage> findByStation(int stationId) {
        return query("SELECT %s FROM cluster_station_storage WHERE station_id = :station_id;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ClusterStationStorage.map())
                .first();
    }

    /** The config of the version a station's bytes were carried to; empty when it stands on no cluster storage. */
    public Optional<StationStorageBackendConfig> findConfigForStation(int stationId) {
        return query("""
                SELECT csc.config
                FROM cluster_station_storage css
                JOIN cluster_storage_config csc ON csc.id = css.config_id
                WHERE css.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> StationStorageBackendConfig.parse(row.getString("config")))
                .first();
    }

    /** The placements of every station whose bytes are on one cluster's storage. */
    public List<ClusterStationStorage> findByCluster(int clusterId) {
        return query("SELECT %s FROM cluster_station_storage WHERE cluster_id = :cluster_id;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStationStorage.map())
                .all();
    }

    /** Every station on some cluster's storage, which the instance-wide swap leaves alone. */
    public Set<Integer> findAllStationIds() {
        return new HashSet<>(query("SELECT station_id FROM cluster_station_storage;")
                .single()
                .map(row -> row.getInt("station_id"))
                .all());
    }

    /** Records that a station's bytes now sit on a version of its cluster's storage. */
    public void place(int stationId, int clusterId, int configId) {
        query("""
                INSERT INTO cluster_station_storage (station_id, cluster_id, config_id, moved_at)
                VALUES (:station_id, :cluster_id, :config_id, now())
                ON CONFLICT (station_id)
                DO UPDATE SET cluster_id = excluded.cluster_id,
                              config_id = excluded.config_id,
                              moved_at = now();""")
                .single(call().bind("station_id", stationId)
                        .bind("cluster_id", clusterId)
                        .bind("config_id", configId))
                .update();
    }

    /** Records that a station's bytes have left its cluster's storage, if they were on it. */
    public void remove(int stationId) {
        query("DELETE FROM cluster_station_storage WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }
}
