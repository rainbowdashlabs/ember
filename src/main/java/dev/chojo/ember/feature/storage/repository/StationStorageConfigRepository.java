/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * CRUD for the per-station remote-backend override. At most one row per station; the override
 * applies across every station-scoped movable category. Credentials inside the JSONB payload
 * are encrypted by the caller (route layer) before they arrive here.
 */
@Singleton
public class StationStorageConfigRepository {

    /**
     * Returns the set of station ids that currently carry a backend override. Used by the
     * instance-wide migration to skip stations whose bytes already live somewhere other than
     * the instance default.
     */
    public Set<Integer> findAllStationIds() {
        return new HashSet<>(query("SELECT station_id FROM station_storage_config;")
                .single()
                .map(row -> row.getInt("station_id"))
                .all());
    }

    /**
     * Returns the station's override, when one exists.
     */
    public Optional<Row> findOne(int stationId) {
        return query("""
                SELECT station_id, backend_type, config
                FROM station_storage_config
                WHERE station_id = :station_id;
                """)
                .single(call().bind("station_id", stationId))
                .map(row ->
                        new Row(row.getInt("station_id"), StationStorageBackendConfig.parse(row.getString("config"))))
                .first();
    }

    /**
     * Insert-or-update the station's override with storage of the station's own, which no other
     * installation shares.
     */
    public void upsert(int stationId, StationStorageBackendConfig config) {
        write(stationId, config, false);
    }

    /**
     * Insert-or-update the station's override with the storage it took over from the installation it
     * moved here from, which that installation may still keep files in.
     */
    public void adoptFromTransfer(int stationId, StationStorageBackendConfig config) {
        write(stationId, config, true);
    }

    /**
     * Whether the station's storage was taken over from the installation it moved here from, and is
     * still the storage it stands on.
     */
    public boolean isSharedByTransfer(int stationId) {
        return query("SELECT shared_by_transfer FROM station_storage_config WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getBoolean("shared_by_transfer"))
                .first()
                .orElse(false);
    }

    private void write(int stationId, StationStorageBackendConfig config, boolean sharedByTransfer) {
        query("""
                INSERT INTO station_storage_config (station_id, backend_type, config, shared_by_transfer, updated_at)
                VALUES (:station_id, :backend_type, :config::JSONB, :shared_by_transfer, now())
                ON CONFLICT (station_id)
                DO UPDATE SET backend_type = excluded.backend_type,
                              config = excluded.config,
                              shared_by_transfer = excluded.shared_by_transfer,
                              updated_at = now();
                """)
                .single(call().bind("station_id", stationId)
                        .bind("backend_type", config.type().name())
                        .bind("config", config.toJson())
                        .bind("shared_by_transfer", sharedByTransfer))
                .update();
    }

    /**
     * Removes the station's override; no-op when none exists.
     */
    public void delete(int stationId) {
        query("DELETE FROM station_storage_config WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }

    /**
     * Read-side projection of one row. {@code backend_type} on the column is redundant with
     * {@link StationStorageBackendConfig#type()}; the entity is the source of truth.
     */
    public record Row(int stationId, StationStorageBackendConfig config) {}
}
