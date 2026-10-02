/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.notifications.entity.DigestItem;
import dev.chojo.ember.feature.notifications.entity.Notification;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.Recipient;
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
 *
 * <p>A station member's and a cluster member's notifications share the table and differ only in the
 * column naming their reader, so every read and write of a feed takes the {@link Recipient} and asks
 * the same statement of that column.
 */
@Singleton
public class NotificationRepository {
    private static final String NOTIFICATION_COLUMNS =
            "id, member_id, cluster_member_id, type, data, created_at, acknowledged_at";

    /**
     * Writes one notification to every reader the {@code wanted} statement names, with the reader's
     * column and that statement filled in. A reader with the same notification still unread is
     * skipped for {@link Delivery#ONCE_WHILE_UNREAD}, also when two requests write at once, through
     * the partial unique index on the reader column and the dedup key.
     */
    private static final String INSERT_FOR_READERS = """
            WITH wanted(reader_id) AS (%2$s)
            INSERT INTO notification (%1$s, type, data, dedup_key)
            SELECT w.reader_id, :type, :data::JSONB,
                   CASE WHEN :once THEN md5(:type::TEXT || :data::JSONB::TEXT) END
            FROM wanted w
            WHERE w.reader_id <> ALL(:excluded::INT[])
              AND NOT (:once AND exists (
                    SELECT 1 FROM notification waiting
                    WHERE waiting.%1$s = w.reader_id AND waiting.acknowledged_at IS NULL
                      AND waiting.type = :type AND waiting.data = :data::JSONB))
            ON CONFLICT (%1$s, dedup_key)
                WHERE acknowledged_at IS NULL AND dedup_key IS NOT NULL AND %1$s IS NOT NULL
                DO NOTHING
            RETURNING %1$s;""";

    private static final String STATION_READERS = """
            SELECT sm.id
            FROM (
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
            ) AS reached(member_id)
            JOIN station_member sm ON sm.id = reached.member_id AND NOT sm.former
            WHERE NOT exists (
                    SELECT 1 FROM user_notification_settings s
                    WHERE s.member_id = sm.id AND s.notification_type = :type AND NOT s.app_enabled)""".formatted(PermissionHolderSql.HOLDS_PERMISSION);

    private static final String CLUSTER_READERS = "SELECT cm.id FROM cluster_member cm WHERE cm.id = ANY(:ids::INT[])";

    /**
     * Retrieves all unacknowledged notifications of a reader, ordered by creation time descending.
     *
     * @param recipient whose feed
     * @return list of unacknowledged notifications
     */
    public List<Notification> findUnacknowledged(Recipient recipient) {
        var feed = Feed.of(recipient);
        return query("""
                SELECT %s FROM notification
                WHERE %s = :reader AND acknowledged_at IS NULL
                ORDER BY created_at DESC;""", NOTIFICATION_COLUMNS, feed.column())
                .single(call().bind("reader", feed.id()))
                .map(Notification.map())
                .all();
    }

    /**
     * Retrieves the most recent 50 notifications of a reader, regardless of acknowledgement status.
     *
     * @param recipient whose feed
     * @return list of notifications
     */
    public List<Notification> findRecent(Recipient recipient) {
        var feed = Feed.of(recipient);
        return query("""
                SELECT %s FROM notification
                WHERE %s = :reader
                ORDER BY created_at DESC LIMIT 50;""", NOTIFICATION_COLUMNS, feed.column())
                .single(call().bind("reader", feed.id()))
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
     * Counts the unacknowledged notifications of a reader.
     *
     * @param recipient whose feed
     * @return count of unacknowledged notifications
     */
    public int countUnacknowledged(Recipient recipient) {
        var feed = Feed.of(recipient);
        return count("""
                SELECT count(*) AS cnt FROM notification
                WHERE %s = :reader AND acknowledged_at IS NULL;""", call().bind("reader", feed.id()), feed.column());
    }

    /**
     * Acknowledges a single notification by setting its acknowledged timestamp.
     *
     * @param recipient whose feed it must be in, so nobody acknowledges somebody else's
     * @param id        the notification ID
     * @return {@code true} if the notification was theirs and unread
     */
    public boolean acknowledge(Recipient recipient, int id) {
        var feed = Feed.of(recipient);
        return query("""
                UPDATE notification SET acknowledged_at = :now
                WHERE id = :id AND %s = :reader AND acknowledged_at IS NULL;""", feed.column())
                .single(call().bind("id", id).bind("reader", feed.id()).bind("now", Instant.now(), INSTANT_TIMESTAMP))
                .update()
                .changed();
    }

    /**
     * Acknowledges all unacknowledged notifications of a reader.
     *
     * @param recipient whose feed
     * @return the number of notifications acknowledged
     */
    public int acknowledgeAll(Recipient recipient) {
        var feed = Feed.of(recipient);
        return query("""
                UPDATE notification SET acknowledged_at = :now
                WHERE %s = :reader AND acknowledged_at IS NULL;""", feed.column())
                .single(call().bind("reader", feed.id()).bind("now", Instant.now(), INSTANT_TIMESTAMP))
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
        return insertFor(Feed.STATION_COLUMN, STATION_READERS, PermissionHolderSql.bind(call, audience.permissions()));
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
        return insertFor(
                Feed.CLUSTER_COLUMN,
                CLUSTER_READERS,
                call().bind("ids", List.copyOf(clusterMemberIds), PostgreSqlTypes.INTEGER)
                        .bind("excluded", List.copyOf(excluded), PostgreSqlTypes.INTEGER)
                        .bind("type", type)
                        .bind("data", data.toJson())
                        .bind("once", delivery == Delivery.ONCE_WHILE_UNREAD));
    }

    private static int insertFor(String column, String readers, Call call) {
        return query(INSERT_FOR_READERS, column, readers)
                .single(call)
                .map(row -> row.getInt(1))
                .all()
                .size();
    }

    /**
     * Snapshot of the latest notification state for a member.
     */
    public record Stamp(int maxId, Instant maxCreatedAt) {}

    /**
     * Which column names the reader of a feed, and their id in it.
     *
     * @param column the reader column, one of the two constants
     * @param id     the station member or cluster member
     */
    private record Feed(String column, int id) {
        private static final String STATION_COLUMN = "member_id";
        private static final String CLUSTER_COLUMN = "cluster_member_id";

        private static Feed of(Recipient recipient) {
            return switch (recipient) {
                case Recipient.OfStation station -> new Feed(STATION_COLUMN, station.memberId());
                case Recipient.OfCluster cluster -> new Feed(CLUSTER_COLUMN, cluster.clusterMemberId());
            };
        }
    }
}
