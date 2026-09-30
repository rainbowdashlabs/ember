/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.util.sql.PermissionHolderSql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * Repository for persisting and querying notifications, including acknowledgement and email digest tracking.
 */
@Singleton
public class NotificationRepository {
    private static final String NOTIFICATION_COLUMNS =
            "id, member_id, cluster_member_id, type, data, created_at, acknowledged_at";

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
     * Deletes unacknowledged notifications of a given type that point at one particular entity,
     * for the case where the entity is still there and only the message has stopped being true.
     *
     * <p>Matching on the link rather than on the message text is what keeps a withdrawal to the one
     * thing it is about: two entities may well carry the same words, and an entity with nothing
     * written about it carries none at all, so a text fragment matches everything.
     *
     * <p>Read notifications stay: their reader has seen them and the link still leads somewhere.
     * Where the entity itself is gone, {@link #deleteAllPointingAt} is the one to use.
     *
     * @param type the notification type to match
     * @param link the link the notification must carry
     * @return the number of notifications deleted
     */
    public int deleteByTypeAndLink(NotificationType type, NotificationData.NotificationLink link) {
        return query("""
                DELETE FROM notification
                WHERE type = :type
                  AND data -> 'link' @> :link::JSONB
                  AND acknowledged_at IS NULL;""")
                .single(call().bind("type", type).bind("link", link.toJson()))
                .delete()
                .rows();
    }

    /**
     * Deletes every notification pointing at one entity, of whatever type and read or not, for the
     * case where the entity itself is gone.
     *
     * <p>A read notification is kept out of nothing here: the entity behind it no longer exists, so
     * tapping it in the feed lands on a page that is not there. Neither is the type asked for, since
     * everything written about a removed entity is a dead end alike.
     *
     * <p>A link that names only some of its route parameters reaches every notification whose link
     * carries at least those, which is how the reminders for one appointment are reached without
     * naming each of their dates.
     *
     * @param link the link the notification must carry
     * @return the number of notifications deleted
     */
    public int deleteAllPointingAt(NotificationData.NotificationLink link) {
        return query("""
                DELETE FROM notification
                WHERE data -> 'link' @> :link::JSONB;""").single(call().bind("link", link.toJson())).delete().rows();
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
     * Every notification not yet mailed, station members' and cluster members' alike, with the group
     * whose mail it goes out with, the account behind its reader and whether they want it by mail.
     *
     * <p>One statement for the whole sweep, so the digest costs the same whether ten people or a
     * thousand have something waiting. A station member wants a notification by mail when their mail
     * switch is on and the mail setting of its type is too; a cluster member has one switch for
     * everything.
     *
     * @return the waiting notifications, grouped by station or cluster and by reader, oldest first
     */
    public List<DigestItem> findWaitingForDigest() {
        return query("""
                SELECT %s,
                       CASE WHEN n.member_id IS NOT NULL THEN 'STATION' ELSE 'CLUSTER' END AS group_kind,
                       coalesce(sm.station_id, cm.cluster_id) AS group_id,
                       coalesce(n.member_id, n.cluster_member_id) AS recipient_id,
                       coalesce(sm.account_id, cm.account_id) AS account_id,
                       CASE WHEN n.member_id IS NOT NULL
                            THEN coalesce(us.email_enabled, FALSE) AND coalesce(uns.email_enabled, FALSE)
                            ELSE cm.email_enabled END AS mail_wanted
                FROM notification n
                LEFT JOIN station_member sm ON sm.id = n.member_id
                LEFT JOIN user_settings us ON us.member_id = n.member_id
                LEFT JOIN user_notification_settings uns
                       ON uns.member_id = n.member_id AND uns.notification_type = n.type
                LEFT JOIN cluster_member cm ON cm.id = n.cluster_member_id
                WHERE n.emailed_at IS NULL
                ORDER BY group_kind DESC, group_id, recipient_id, n.created_at, n.id;""", SqlSupport.alias("n", NOTIFICATION_COLUMNS))
                .single()
                .map(row -> new DigestItem(
                        Notification.map().map(row),
                        new DigestGroup.Key(row.getEnum("group_kind", DigestGroup.Kind.class), row.getInt("group_id")),
                        row.getInt("recipient_id"),
                        row.getObject("account_id", Integer.class),
                        row.getBoolean("mail_wanted")))
                .all();
    }

    /**
     * Marks the given notifications as having been included in an email digest.
     *
     * @param ids the notification IDs to mark
     */
    public void markEmailed(List<Integer> ids) {
        if (ids.isEmpty()) return;
        query("UPDATE notification SET emailed_at = :now WHERE id = ANY(:ids::INT[]);")
                .single(call().bind("now", Instant.now(), INSTANT_TIMESTAMP).bind("ids", ids, PostgreSqlTypes.INTEGER))
                .update();
    }

    /**
     * Writes one notification to every station member the audience reaches, in one statement.
     *
     * <p>The parts of the audience are united in the database, so a member reached twice is told
     * once. Members who have left are dropped, as are the guardians of a ward who has left and
     * everybody who switched this type off in the app. With {@link Delivery#ONCE_WHILE_UNREAD} a
     * member who already has the same notification waiting is skipped, however that one was sent,
     * and the row carries a key that the partial unique index holds unique among a member's unread
     * rows, so the skip also holds when two requests write it at the same moment.
     *
     * @param audience who is meant
     * @param type     the notification category
     * @param data     the message data
     * @param delivery whether an identical unread notification suppresses this one
     * @return how many rows were written
     */
    public int insertForStation(
            StationAudience audience, NotificationType type, NotificationData data, Delivery delivery) {
        var call = call().bind("member_ids", List.copyOf(audience.memberIds()), PostgreSqlTypes.INTEGER)
                .bind("whole_station_id", audience.wholeStationId())
                .bind("holders_station_id", audience.holdersStationId())
                .bind("ward_ids", List.copyOf(audience.wardIds()), PostgreSqlTypes.INTEGER)
                .bind("excluded", List.copyOf(audience.excluded()), PostgreSqlTypes.INTEGER)
                .bind("type", type)
                .bind("data", data.toJson())
                .bind("once", delivery == Delivery.ONCE_WHILE_UNREAD);
        return query("""
                WITH wanted(member_id) AS (
                        SELECT unnest(:member_ids::INT[])
                    UNION
                        SELECT sm.id FROM station_member sm
                        WHERE sm.station_id = :whole_station_id::INT
                    UNION
                        SELECT sm.id FROM station_member sm
                        WHERE sm.station_id = :holders_station_id::INT AND (%s)
                    UNION
                        SELECT mm.manager_id FROM member_manager mm
                        JOIN station_member ward ON ward.id = mm.managed_id AND NOT ward.former
                        WHERE mm.managed_id = ANY(:ward_ids::INT[])
                )
                INSERT INTO notification (member_id, type, data, dedup_key)
                SELECT w.member_id, :type, :data::JSONB,
                       CASE WHEN :once THEN md5(:type::TEXT || :data::JSONB::TEXT) END
                FROM wanted w
                JOIN station_member sm ON sm.id = w.member_id AND NOT sm.former
                WHERE w.member_id <> ALL(:excluded::INT[])
                  AND NOT exists (
                        SELECT 1 FROM user_notification_settings s
                        WHERE s.member_id = w.member_id AND s.notification_type = :type AND NOT s.app_enabled)
                  AND NOT (:once AND exists (
                        SELECT 1 FROM notification waiting
                        WHERE waiting.member_id = w.member_id AND waiting.acknowledged_at IS NULL
                          AND waiting.type = :type AND waiting.data = :data::JSONB))
                ON CONFLICT (member_id, dedup_key)
                    WHERE acknowledged_at IS NULL AND dedup_key IS NOT NULL AND member_id IS NOT NULL
                    DO NOTHING
                RETURNING member_id;""", PermissionHolderSql.HOLDS_PERMISSION)
                .single(PermissionHolderSql.bind(call, audience.permissions()))
                .map(row -> row.getInt(1))
                .all()
                .size();
    }

    /**
     * Writes one notification to each of the given cluster members, in one statement.
     *
     * <p>The cluster twin of {@link #insertForStation}. Cluster members have no per-type settings, so
     * nobody is left out for a preference.
     *
     * @param clusterMemberIds the cluster members meant
     * @param excluded         cluster members left out whatever else reaches them
     * @param type             the notification category
     * @param data             the message data
     * @param delivery         whether an identical unread notification suppresses this one
     * @return how many rows were written
     */
    public int insertForCluster(
            Collection<Integer> clusterMemberIds,
            Collection<Integer> excluded,
            NotificationType type,
            NotificationData data,
            Delivery delivery) {
        return query("""
                INSERT INTO notification (cluster_member_id, type, data, dedup_key)
                SELECT cm.id, :type, :data::JSONB,
                       CASE WHEN :once THEN md5(:type::TEXT || :data::JSONB::TEXT) END
                FROM cluster_member cm
                WHERE cm.id = ANY(:ids::INT[]) AND cm.id <> ALL(:excluded::INT[])
                  AND NOT (:once AND exists (
                        SELECT 1 FROM notification waiting
                        WHERE waiting.cluster_member_id = cm.id AND waiting.acknowledged_at IS NULL
                          AND waiting.type = :type AND waiting.data = :data::JSONB))
                ON CONFLICT (cluster_member_id, dedup_key)
                    WHERE acknowledged_at IS NULL AND dedup_key IS NOT NULL AND cluster_member_id IS NOT NULL
                    DO NOTHING
                RETURNING cluster_member_id;""")
                .single(call().bind("ids", List.copyOf(clusterMemberIds), PostgreSqlTypes.INTEGER)
                        .bind("excluded", List.copyOf(excluded), PostgreSqlTypes.INTEGER)
                        .bind("type", type)
                        .bind("data", data.toJson())
                        .bind("once", delivery == Delivery.ONCE_WHILE_UNREAD))
                .map(row -> row.getInt(1))
                .all()
                .size();
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
