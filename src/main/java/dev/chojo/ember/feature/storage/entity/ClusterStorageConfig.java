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
 * One version of the storage an association keeps for itself and its stations.
 *
 * <p>Versioned because a placement points at where bytes were actually carried. Changing the destination
 * makes a new current version and leaves the old one readable for whoever is still standing on it; changing
 * only the credentials edits the version in place and moves nobody.
 *
 * @param id        what a placement points at
 * @param clusterId the association this storage belongs to
 * @param config    the backend, with its credentials still encrypted
 * @param current   whether this is the version new placements are carried to
 * @param createdAt when the version was first set
 * @param updatedAt when its credentials were last edited
 */
public record ClusterStorageConfig(
        int id,
        int clusterId,
        StationStorageBackendConfig config,
        boolean current,
        Instant createdAt,
        Instant updatedAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterStorageConfig> map() {
        return row -> new ClusterStorageConfig(
                row.getInt("id"),
                row.getInt("cluster_id"),
                StationStorageBackendConfig.parse(row.getString("config")),
                row.getBoolean("is_current"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
