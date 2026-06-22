/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.repository;

import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageUsage;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for station storage usage tracking.
 * Supports both incremental delta updates and full recalculation.
 */
@Singleton
public class StorageUsageRepository {

    /**
     * Returns all storage usage records for a station.
     */
    public List<StorageUsage> findByStation(int stationId) {
        return query(
                        "SELECT station_id, category, total_bytes, file_count, updated_at FROM station_storage_usage WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(StorageUsage.map())
                .all();
    }

    /**
     * Returns storage usage for a specific station and category.
     */
    public Optional<StorageUsage> findByStationAndCategory(int stationId, StorageCategory category) {
        return query(
                        "SELECT station_id, category, total_bytes, file_count, updated_at FROM station_storage_usage WHERE station_id = :station_id AND category = :category;")
                .single(call().bind("station_id", stationId).bind("category", category.name()))
                .map(StorageUsage.map())
                .first();
    }

    /**
     * Applies a delta to the usage for a station/category (increment or decrement).
     * Uses UPSERT to create the record if it does not exist.
     */
    public void applyDelta(int stationId, StorageCategory category, long bytesDelta, int fileCountDelta) {
        query("""
                INSERT INTO station_storage_usage (station_id, category, total_bytes, file_count, updated_at)
                VALUES (:station_id, :category, GREATEST(0, :bytes), GREATEST(0, :files), now())
                ON CONFLICT (station_id, category)
                DO UPDATE SET total_bytes = GREATEST(0, station_storage_usage.total_bytes + :bytes),
                              file_count  = GREATEST(0, station_storage_usage.file_count + :files),
                              updated_at  = now();
                """)
                .single(call().bind("station_id", stationId)
                        .bind("category", category.name())
                        .bind("bytes", bytesDelta)
                        .bind("files", fileCountDelta))
                .insert();
    }

    /**
     * Sets the absolute usage for a station/category (used by reconciliation).
     */
    public void setUsage(int stationId, StorageCategory category, long totalBytes, int fileCount) {
        query("""
                INSERT INTO station_storage_usage (station_id, category, total_bytes, file_count, updated_at)
                VALUES (:station_id, :category, :bytes, :files, now())
                ON CONFLICT (station_id, category)
                DO UPDATE SET total_bytes = :bytes,
                              file_count  = :files,
                              updated_at  = now();
                """)
                .single(call().bind("station_id", stationId)
                        .bind("category", category.name())
                        .bind("bytes", totalBytes)
                        .bind("files", fileCount))
                .insert();
    }

    /**
     * Returns the total bytes used across all categories for a station (excluding AVATARS).
     */
    public long totalEnforcedBytes(int stationId) {
        return query(
                        "SELECT COALESCE(SUM(total_bytes), 0) AS total FROM station_storage_usage WHERE station_id = :station_id AND category NOT IN ('AVATARS', 'IMAGE_AVATAR', 'DOCUMENT', 'DISCOVERY_KEY', 'MAP_TILE_CACHE', 'DEMO_AVATAR');")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getLong("total"))
                .first()
                .orElse(0L);
    }

    /**
     * Returns usage for a specific category.
     */
    public long categoryBytes(int stationId, StorageCategory category) {
        return query(
                        "SELECT COALESCE(total_bytes, 0) AS total FROM station_storage_usage WHERE station_id = :station_id AND category = :category;")
                .single(call().bind("station_id", stationId).bind("category", category.name()))
                .map(row -> row.getLong("total"))
                .first()
                .orElse(0L);
    }

    /**
     * Returns all usage records across all stations (for admin overview).
     */
    public List<StorageUsage> findAll() {
        return query(
                        "SELECT station_id, category, total_bytes, file_count, updated_at FROM station_storage_usage ORDER BY station_id, category;")
                .single(call())
                .map(StorageUsage.map())
                .all();
    }

    /**
     * Deletes all usage records for a station.
     */
    public void deleteByStation(int stationId) {
        query("DELETE FROM station_storage_usage WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }
}
