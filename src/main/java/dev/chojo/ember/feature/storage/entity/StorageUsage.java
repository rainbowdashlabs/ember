/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Tracks storage usage for a single category within a station.
 *
 * @param stationId  the station this usage belongs to
 * @param category   the storage category
 * @param totalBytes the total bytes used in this category
 * @param fileCount  the number of files in this category
 * @param updatedAt  when this record was last updated
 */
public record StorageUsage(int stationId, StorageCategory category, long totalBytes, int fileCount, Instant updatedAt) {
    public static RowMapping<StorageUsage> map() {
        return row -> new StorageUsage(
                row.getInt("station_id"),
                row.getEnum("category", StorageCategory.class),
                row.getLong("total_bytes"),
                row.getInt("file_count"),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
