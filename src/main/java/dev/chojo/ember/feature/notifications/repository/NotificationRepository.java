/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/**
 * Repository for persisting and querying notifications, including acknowledgement and email digest tracking.
 */
@Singleton
public class NotificationRepository {
    private static final String NOTIFICATION_COLUMNS =
            "id, member_id, cluster_member_id, type, data, created_at, acknowledged_at";

    /**
     * Creates a new notification for a member.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     * @return the persisted notification
     */
    public Notification create(int memberId, NotificationType type, NotificationData data) {
        return insertReturning(
                """
                INSERT INTO notification(member_id, type, data)
                VALUES(:member_id, :type, :data::JSONB)
                RETURNING %s;""".formatted(NOTIFICATION_COLUMNS),
                call().bind("member_id", memberId).bind("type", type).bind("data", data.toJson()),
                Notification.map(),
                NOTIFICATION_COLUMNS);
    }

    /**
     * Checks whether an unacknowledged notification with the exact same type and data already exists for a member.
     *
     * @param memberId the member ID
     * @param type     the notification type
     * @param data     the notification data that must match exactly
     * @return {@code true} if a matching unacknowledged notification exists
     */
    public boolean exists(int memberId, NotificationType type, NotificationData data) {
        return SqlSupport.exists(
                """
                SELECT 1 FROM notification
                WHERE member_id = :member_id
                  AND type = :type
                  AND data = :data::JSONB
                  AND acknowledged_at IS NULL;""", call().bind("member_id", memberId).bind("type", type).bind("data", data.toJson()));
    }

    /**
     * Retrieves all unacknowledged notifications for a member, ordered by creation time descending.
     *
     * @param memberId the member ID
     * @return list of unacknowledged notifications
     */
    public List<Notification> findUnacknowledged(int memberId) {
        return query(
                        "SELECT %s FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL ORDER BY created_at DESC;",
                        NOTIFICATION_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(Notification.map())
                .all();
    }

    /**
     * Retrieves the most recent 50 notifications for a member, regardless of acknowledgement status.
     *
     * @param memberId the member ID
     * @return list of notifications
     */
    public List<Notification> findAll(int memberId) {
        return query(
                        "SELECT %s FROM notification WHERE member_id = :member_id ORDER BY created_at DESC LIMIT 50;",
                        NOTIFICATION_COLUMNS)
                .single(call().bind("member_id", memberId))
                .map(Notification.map())
                .all();
    }

    /**
     * Returns the highest notification id and the most recent {@code created_at} for the given
     * member. Used to derive an ETag for the personal RSS/Atom feed without enumerating rows.
     *
     * @return a stamp where {@code id == 0} means the member has no notifications
     */
    public Stamp findMaxStamp(int memberId) {
        return query(
                        "SELECT coalesce(max(id), 0) AS max_id, max(created_at) AS max_at FROM notification WHERE member_id = :member_id;")
                .single(call().bind("member_id", memberId))
                .map(row -> {
                    Instant at = row.get("max_at", INSTANT_TIMESTAMP);
                    return new Stamp(row.getInt("max_id"), at != null ? at : Instant.EPOCH);
                })
                .first()
                .orElse(new Stamp(0, Instant.EPOCH));
    }

    /**
     * Counts unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return count of unacknowledged notifications
     */
    public int countUnacknowledged(int memberId) {
        return count(
                "SELECT count(*) AS cnt FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL;",
                call().bind("member_id", memberId));
    }

    /**
     * Acknowledges a single notification by setting its acknowledged timestamp.
     *
     * @param id       the notification ID
     * @param memberId the member ID (for ownership verification)
     * @return {@code true} if the notification was acknowledged
     */
    public boolean acknowledge(int id, int memberId) {
        return query(
                        "UPDATE notification SET acknowledged_at = :now WHERE id = :id AND member_id = :member_id AND acknowledged_at IS NULL;")
                .single(call().bind("id", id).bind("member_id", memberId).bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Acknowledges all unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return the number of notifications acknowledged
     */
    public int acknowledgeAll(int memberId) {
        return query(
                        "UPDATE notification SET acknowledged_at = :now WHERE member_id = :member_id AND acknowledged_at IS NULL;")
                .single(call().bind("member_id", memberId).bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .rows();
    }

    /**
     * Deletes unacknowledged notifications of a given type whose data contains the specified JSON fragment.
     * Uses PostgreSQL's {@code @>} containment operator for partial matching.
     *
     * @param type        the notification type to match
     * @param partialData the data fragment that must be contained in the notification data
     * @return the number of notifications deleted
     */
    public int deleteByTypeContaining(NotificationType type, NotificationData partialData) {
        return query("""
                DELETE FROM notification
                WHERE type = :type
                  AND data @> :partial::JSONB
                  AND acknowledged_at IS NULL;""")
                .single(call().bind("type", type).bind("partial", partialData.toJson()))
                .delete()
                .rows();
    }

    /**
     * Deletes acknowledged notifications older than 30 days.
     */
    public void deleteOldAcknowledged() {
        query(
                        "DELETE FROM notification WHERE acknowledged_at IS NOT NULL AND acknowledged_at < now() - INTERVAL '30 days';")
                .single()
                .delete();
    }

    /**
     * Retrieves all notifications that have not yet been included in an email digest.
     *
     * @return list of unemailed notifications, ordered by member and creation time
     */
    public List<Notification> findUnemailed() {
        return query("""
                SELECT %s FROM notification
                WHERE emailed_at IS NULL AND member_id IS NOT NULL
                ORDER BY member_id, created_at;""", NOTIFICATION_COLUMNS).single().map(Notification.map()).all();
    }

    /**
     * Marks the given notifications as having been included in an email digest.
     *
     * @param ids the notification IDs to mark
     */
    public void markEmailed(List<Integer> ids) {
        if (ids.isEmpty()) return;
        var now = Instant.now();
        for (int id : ids) {
            query("UPDATE notification SET emailed_at = :now WHERE id = :id;")
                    .single(call().bind("now", now, INSTANT_TIMESTAMP).bind("id", id))
                    .update();
        }
    }

    // -- Cluster members --
    //
    // A parallel set rather than a column parameter on the ones above: the two recipients are different
    // things, and a method that took whichever id you happened to have would let a station member's id
    // silently read a cluster member's feed.

    /**
     * Creates a notification for a cluster member.
     *
     * @param clusterMemberId the target cluster member
     * @param type            the notification category
     * @param data            localized message data
     * @return the persisted notification
     */
    public Notification createForClusterMember(int clusterMemberId, NotificationType type, NotificationData data) {
        return insertReturning(
                """
                INSERT INTO notification(cluster_member_id, type, data)
                VALUES(:cluster_member_id, :type, :data::JSONB)
                RETURNING %s;""".formatted(NOTIFICATION_COLUMNS),
                call().bind("cluster_member_id", clusterMemberId)
                        .bind("type", type)
                        .bind("data", data.toJson()),
                Notification.map(),
                NOTIFICATION_COLUMNS);
    }

    /**
     * Whether the same thing is already waiting unread for this cluster member.
     *
     * @param clusterMemberId the cluster member
     * @param type            the notification type
     * @param data            the data that must match exactly
     * @return {@code true} when one is already there
     */
    public boolean existsForClusterMember(int clusterMemberId, NotificationType type, NotificationData data) {
        return SqlSupport.exists(
                """
                SELECT 1 FROM notification
                WHERE cluster_member_id = :cluster_member_id
                  AND type = :type
                  AND data = :data::JSONB
                  AND acknowledged_at IS NULL;""",
                call().bind("cluster_member_id", clusterMemberId)
                        .bind("type", type)
                        .bind("data", data.toJson()));
    }

    /**
     * The unread notifications of a cluster member, newest first.
     *
     * @param clusterMemberId the cluster member
     * @return what is waiting for them
     */
    public List<Notification> findUnacknowledgedForClusterMember(int clusterMemberId) {
        return query("""
                SELECT %s FROM notification
                WHERE cluster_member_id = :cluster_member_id AND acknowledged_at IS NULL
                ORDER BY created_at DESC;""", NOTIFICATION_COLUMNS)
                .single(call().bind("cluster_member_id", clusterMemberId))
                .map(Notification.map())
                .all();
    }

    /**
     * The last fifty notifications of a cluster member, read or not.
     *
     * @param clusterMemberId the cluster member
     * @return their feed
     */
    public List<Notification> findAllForClusterMember(int clusterMemberId) {
        return query("""
                SELECT %s FROM notification
                WHERE cluster_member_id = :cluster_member_id
                ORDER BY created_at DESC LIMIT 50;""", NOTIFICATION_COLUMNS)
                .single(call().bind("cluster_member_id", clusterMemberId))
                .map(Notification.map())
                .all();
    }

    /**
     * How much a cluster member has not read yet.
     *
     * @param clusterMemberId the cluster member
     * @return the count
     */
    public int countUnacknowledgedForClusterMember(int clusterMemberId) {
        return count("""
                SELECT count(*) AS cnt FROM notification
                WHERE cluster_member_id = :cluster_member_id AND acknowledged_at IS NULL;""", call().bind("cluster_member_id", clusterMemberId));
    }

    /**
     * Marks one of a cluster member's notifications read.
     *
     * @param id              the notification
     * @param clusterMemberId the cluster member, so nobody acknowledges somebody else's
     * @return {@code true} when it was theirs and unread
     */
    public boolean acknowledgeForClusterMember(int id, int clusterMemberId) {
        return query("""
                UPDATE notification SET acknowledged_at = :now
                WHERE id = :id AND cluster_member_id = :cluster_member_id AND acknowledged_at IS NULL;""")
                .single(call().bind("id", id)
                        .bind("cluster_member_id", clusterMemberId)
                        .bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Marks everything a cluster member has waiting as read.
     *
     * @param clusterMemberId the cluster member
     * @return how many were marked
     */
    public int acknowledgeAllForClusterMember(int clusterMemberId) {
        return query("""
                UPDATE notification SET acknowledged_at = :now
                WHERE cluster_member_id = :cluster_member_id AND acknowledged_at IS NULL;""")
                .single(call().bind("cluster_member_id", clusterMemberId).bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .rows();
    }

    /**
     * Snapshot of the latest notification state for a member.
     */
    public record Stamp(int maxId, Instant maxCreatedAt) {}
}
