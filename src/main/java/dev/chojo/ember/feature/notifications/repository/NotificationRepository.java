/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Repository for persisting and querying notifications, including acknowledgement and email digest tracking.
 */
@Singleton
public class NotificationRepository {

    /**
     * Creates a new notification for a member.
     *
     * @param memberId the target member ID
     * @param type     the notification category
     * @param data     localized message data
     * @return the persisted notification
     */
    public Notification create(int memberId, NotificationType type, NotificationData data) {
        return query("""
                            INSERT INTO notification(member_id, type, data)
                            VALUES(:member_id, :type, :data::JSONB)
                            RETURNING *;""")
                .single(call().bind("member_id", memberId).bind("type", type).bind("data", data.toJson()))
                .map(Notification.map())
                .first()
                .orElseThrow();
    }

    /**
     * Checks whether an unacknowledged notification with the exact same type and data already exists for a member.
     *
     * @param memberId the member ID
     * @param type     the notification type
     * @param dataJson the notification data as JSON
     * @return {@code true} if a matching unacknowledged notification exists
     */
    public boolean exists(int memberId, NotificationType type, String dataJson) {
        return query("""
                            SELECT 1 FROM notification
                            WHERE member_id = :member_id
                              AND type = :type
                              AND data = :data::JSONB
                              AND acknowledged_at IS NULL;""")
                .single(call().bind("member_id", memberId).bind("type", type).bind("data", dataJson))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    /**
     * Retrieves all unacknowledged notifications for a member, ordered by creation time descending.
     *
     * @param memberId the member ID
     * @return list of unacknowledged notifications
     */
    public List<Notification> findUnacknowledged(int memberId) {
        return query(
                        "SELECT * FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL ORDER BY created_at DESC;")
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
        return query("SELECT * FROM notification WHERE member_id = :member_id ORDER BY created_at DESC LIMIT 50;")
                .single(call().bind("member_id", memberId))
                .map(Notification.map())
                .all();
    }

    /**
     * Counts unacknowledged notifications for a member.
     *
     * @param memberId the member ID
     * @return count of unacknowledged notifications
     */
    public int countUnacknowledged(int memberId) {
        return query(
                        "SELECT count(*) AS cnt FROM notification WHERE member_id = :member_id AND acknowledged_at IS NULL;")
                .single(call().bind("member_id", memberId))
                .map(row -> row.getInt("cnt"))
                .first()
                .orElse(0);
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
     * @param type            the notification type to match
     * @param partialDataJson a JSON fragment that must be contained in the notification data
     * @return the number of notifications deleted
     */
    public int deleteByTypeContaining(NotificationType type, String partialDataJson) {
        return query("""
                            DELETE FROM notification
                            WHERE type = :type
                              AND data @> :partial::JSONB
                              AND acknowledged_at IS NULL;""")
                .single(call().bind("type", type).bind("partial", partialDataJson))
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
                SELECT * FROM notification
                WHERE emailed_at IS NULL
                ORDER BY member_id, created_at;""").single().map(Notification.map()).all();
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
}
