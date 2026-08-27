/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

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

    /**
     * A usage row, or nothing where it counts a category this version does not know.
     *
     * <p>The name of a category is written into the row that counts it, so renaming one in a release
     * leaves rows behind naming something that no longer exists. Read strictly, the first such row
     * took the operator's whole storage overview down with it. A row nothing can be attributed to is
     * left out instead, which is what the reader would do with it anyway.
     *
     * @return the row, empty where its category is not one of {@link StorageCategory}
     */
    public static RowMapping<Optional<StorageUsage>> mapKnown() {
        return row -> {
            var category = known(row.getString("category"));
            if (category.isEmpty()) return Optional.empty();
            return Optional.of(new StorageUsage(
                    row.getInt("station_id"),
                    category.get(),
                    row.getLong("total_bytes"),
                    row.getInt("file_count"),
                    row.get("updated_at", INSTANT_TIMESTAMP)));
        };
    }

    /** The category of that name, where this version has one. */
    private static Optional<StorageCategory> known(String name) {
        return Arrays.stream(StorageCategory.values())
                .filter(candidate -> candidate.name().equals(name))
                .findFirst();
    }
}
