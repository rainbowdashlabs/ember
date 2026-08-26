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
 * Where the stations standing on a cluster's storage actually are.
 *
 * <p>A row here is a copy that finished. Nothing writes one because somebody decided something, which is
 * the whole difference between this table and the policy on the cluster row.
 */
@Singleton
public class ClusterStationStorageRepository {
    private static final String COLUMNS = "station_id, cluster_id, config_id, moved_at";

    /**
     * Where one station's bytes are.
     *
     * @param stationId the station
     * @return its placement, or empty when its bytes are on its own backend or the instance default
     */
    public Optional<ClusterStationStorage> findByStation(int stationId) {
        return query("SELECT %s FROM cluster_station_storage WHERE station_id = :station_id;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ClusterStationStorage.map())
                .first();
    }

    /**
     * The backend one station's bytes are on, built from the version it was carried to.
     *
     * @param stationId the station
     * @return the configuration to build, or empty when the station stands on no cluster storage
     */
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

    /**
     * Every station of one cluster whose bytes are on its storage.
     *
     * @param clusterId the cluster
     * @return their placements
     */
    public List<ClusterStationStorage> findByCluster(int clusterId) {
        return query("SELECT %s FROM cluster_station_storage WHERE cluster_id = :cluster_id;", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterStationStorage.map())
                .all();
    }

    /**
     * Every station standing on some cluster's storage, whichever cluster it is.
     *
     * <p>What the instance-wide swap has to leave alone: those bytes are not on the disk it is swapping.
     *
     * @return their identifiers
     */
    public Set<Integer> findAllStationIds() {
        return new HashSet<>(query("SELECT station_id FROM cluster_station_storage;")
                .single()
                .map(row -> row.getInt("station_id"))
                .all());
    }

    /**
     * How many stations stand on one version, which is what says whether it may be deleted.
     *
     * @param configId the version
     * @return how many placements point at it
     */
    public int countOn(int configId) {
        return query("SELECT count(*) AS placed FROM cluster_station_storage WHERE config_id = :config_id;")
                .single(call().bind("config_id", configId))
                .map(row -> row.getInt("placed"))
                .first()
                .orElse(0);
    }

    /**
     * Records that a station's bytes now sit on a version of its cluster's storage.
     *
     * @param stationId the station whose bytes were carried
     * @param clusterId the cluster whose storage they are on
     * @param configId  the version they were carried to
     */
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

    /**
     * Records that a station's bytes have left its cluster's storage; no-op when they never were on it.
     *
     * @param stationId the station
     */
    public void remove(int stationId) {
        query("DELETE FROM cluster_station_storage WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }
}
