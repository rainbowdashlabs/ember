/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.repository;

import dev.chojo.ember.feature.cluster.entity.ClusterApplication;
import dev.chojo.ember.feature.cluster.entity.ClusterApplicationStatus;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Requests from standing stations to join a cluster.
 *
 * <p>One row per station and cluster whatever became of it, so a station that was refused once and asks again
 * reopens the same row rather than stacking a second one beside it. The history that matters, who asked and
 * who decided, is on the row itself.
 */
@Singleton
public class ClusterApplicationRepository {

    private static final String COLUMNS =
            "id, cluster_id, station_id, requested_by, requested_at, status, deny_reason, resolved_at, resolved_by";

    /**
     * Opens an application, or reopens the one this station already had with this cluster.
     *
     * @param clusterId   the cluster being asked
     * @param stationId   the station asking
     * @param requestedBy the station member who asked
     * @return the pending application
     */
    public ClusterApplication open(int clusterId, int stationId, Integer requestedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    cluster_application(cluster_id, station_id, requested_by)
                VALUES
                    (:cluster_id, :station_id, :requested_by)
                ON CONFLICT (cluster_id, station_id) DO UPDATE
                    SET requested_by = EXCLUDED.requested_by,
                        requested_at = NOW(),
                        status       = 'PENDING',
                        deny_reason  = NULL,
                        resolved_at  = NULL,
                        resolved_by  = NULL
                RETURNING %s;""",
                call().bind("cluster_id", clusterId)
                        .bind("station_id", stationId)
                        .bind("requested_by", requestedBy),
                ClusterApplication.map(),
                COLUMNS);
    }

    public Optional<ClusterApplication> findById(int id) {
        return SqlSupport.findById("cluster_application", COLUMNS, id, ClusterApplication.map());
    }

    /** Every application a cluster has ever received, newest first. */
    public List<ClusterApplication> findByCluster(int clusterId) {
        return query("""
                SELECT %s FROM cluster_application
                WHERE cluster_id = :cluster_id
                ORDER BY requested_at DESC;""", COLUMNS)
                .single(call().bind("cluster_id", clusterId))
                .map(ClusterApplication.map())
                .all();
    }

    /** Every application a station has open or closed, newest first. */
    public List<ClusterApplication> findByStation(int stationId) {
        return query("""
                SELECT %s FROM cluster_application
                WHERE station_id = :station_id
                ORDER BY requested_at DESC;""", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ClusterApplication.map())
                .all();
    }

    /** The one application a station currently has waiting, wherever it was sent. */
    public Optional<ClusterApplication> findPendingForStation(int stationId) {
        return query("""
                SELECT %s FROM cluster_application
                WHERE station_id = :station_id AND status = 'PENDING'
                ORDER BY requested_at DESC
                LIMIT 1;""", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(ClusterApplication.map())
                .first();
    }

    /**
     * Closes an application with the outcome somebody decided on.
     *
     * @param id         the application
     * @param status     what it became
     * @param denyReason the reason, when it was refused
     * @param resolvedBy the cluster member who decided, or {@code null} when the station withdrew
     * @return {@code true} when a pending row was closed
     */
    public boolean resolve(int id, ClusterApplicationStatus status, String denyReason, Integer resolvedBy) {
        return query("""
                UPDATE cluster_application
                SET status      = :status,
                    deny_reason = :deny_reason,
                    resolved_at = NOW(),
                    resolved_by = :resolved_by
                WHERE id = :id AND status = 'PENDING';""")
                .single(call().bind("id", id)
                        .bind("status", status)
                        .bind("deny_reason", denyReason)
                        .bind("resolved_by", resolvedBy))
                .update()
                .changed();
    }
}
