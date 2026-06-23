/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.StationStorageBackendConfig;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * CRUD for the per-station remote-backend overrides written by station managers via the
 * self-service UI. Each row binds one movable storage category at one station to a backend
 * the station owns; credentials inside the JSONB payload are already encrypted by the caller
 * (the route layer) before they arrive here.
 */
@Singleton
public class StationStorageConfigRepository {

    /** Returns every override row owned by {@code stationId}. */
    public List<Row> findByStation(int stationId) {
        return query("""
                SELECT station_id, category, backend_type, config
                FROM station_storage_config
                WHERE station_id = :station_id;
                """)
                .single(call().bind("station_id", stationId))
                .map(row -> new Row(
                        row.getInt("station_id"),
                        row.getEnum("category", StorageCategory.class),
                        StationStorageBackendConfig.parse(row.getString("config"))))
                .all();
    }

    /** Returns the override for one {@code (station, category)} pair, when present. */
    public Optional<Row> findOne(int stationId, StorageCategory category) {
        return query("""
                SELECT station_id, category, backend_type, config
                FROM station_storage_config
                WHERE station_id = :station_id AND category = :category;
                """)
                .single(call().bind("station_id", stationId).bind("category", category.name()))
                .map(row -> new Row(
                        row.getInt("station_id"),
                        row.getEnum("category", StorageCategory.class),
                        StationStorageBackendConfig.parse(row.getString("config"))))
                .first();
    }

    /** Insert-or-update a station's override for {@code category}. */
    public void upsert(int stationId, StorageCategory category, StationStorageBackendConfig config) {
        query("""
                INSERT INTO station_storage_config (station_id, category, backend_type, config, updated_at)
                VALUES (:station_id, :category, :backend_type, :config::jsonb, now())
                ON CONFLICT (station_id, category)
                DO UPDATE SET backend_type = EXCLUDED.backend_type,
                              config = EXCLUDED.config,
                              updated_at = now();
                """)
                .single(call().bind("station_id", stationId)
                        .bind("category", category.name())
                        .bind("backend_type", config.type().name())
                        .bind("config", config.toJson()))
                .update();
    }

    /** Removes the override for {@code (station, category)}; no-op when no row exists. */
    public void delete(int stationId, StorageCategory category) {
        query("""
                DELETE FROM station_storage_config
                WHERE station_id = :station_id AND category = :category;
                """)
                .single(call().bind("station_id", stationId).bind("category", category.name()))
                .delete();
    }

    /**
     * Read-side projection of one row. The {@code backend_type} column is redundant with
     * {@link StationStorageBackendConfig#type()} and exists in the schema only for indexed
     * lookups; the entity is the source of truth.
     */
    public record Row(int stationId, StorageCategory category, StationStorageBackendConfig config) {}
}
