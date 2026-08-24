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
 * Where one station's bytes are, when they are on an association's storage.
 *
 * <p>The row exists because the copy finished, and for no other reason. A station whose association decided
 * something it has not been moved to yet has no row here, which is exactly what makes it out of place.
 *
 * @param stationId the station whose bytes were carried
 * @param clusterId the association whose storage they are on
 * @param configId  the version of that storage they were carried to
 * @param movedAt   when the copy finished
 */
public record ClusterStationStorage(int stationId, int clusterId, int configId, Instant movedAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterStationStorage> map() {
        return row -> new ClusterStationStorage(
                row.getInt("station_id"),
                row.getInt("cluster_id"),
                row.getInt("config_id"),
                row.get("moved_at", INSTANT_TIMESTAMP));
    }
}
