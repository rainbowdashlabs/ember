/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A station owner asking to join a cluster.
 *
 * @param id          the application
 * @param clusterId   the cluster being asked
 * @param stationId   the station asking
 * @param requestedBy the station member who asked, or {@code null} once they have left
 * @param requestedAt when it was opened
 * @param status      where it stands
 * @param denyReason  what the cluster said when it refused, or {@code null}
 * @param resolvedAt  when it stopped being pending, or {@code null}
 * @param resolvedBy  the cluster member who decided, or {@code null}
 */
public record ClusterApplication(
        int id,
        int clusterId,
        int stationId,
        Integer requestedBy,
        Instant requestedAt,
        ClusterApplicationStatus status,
        String denyReason,
        Instant resolvedAt,
        Integer resolvedBy) {

    public static RowMapping<ClusterApplication> map() {
        return row -> new ClusterApplication(
                row.getInt("id"),
                row.getInt("cluster_id"),
                row.getInt("station_id"),
                row.getObject("requested_by", Integer.class),
                row.get("requested_at", INSTANT_TIMESTAMP),
                row.getEnum("status", ClusterApplicationStatus.class),
                row.getString("deny_reason"),
                row.get("resolved_at", INSTANT_TIMESTAMP),
                row.getObject("resolved_by", Integer.class));
    }
}
