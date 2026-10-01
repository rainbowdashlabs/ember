/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.notifications.entity.DigestGroup;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * When a station or a cluster asked to be written to, and when it last was.
 *
 * <p>Kept here rather than on the station itself. Two columns that only the sweep and one settings
 * screen ever read do not belong in a record twenty fields wide that half the application builds by
 * hand, and a station's own repository has no reason to know what a digest is.
 */
@Singleton
public class NotificationScheduleRepository {

    /**
     * The stations and clusters that have something waiting, with what the digest needs of each.
     *
     * <p>One statement for both kinds. A cluster's clock and language are its home station's, which
     * is where its people are: read on the instance's clock instead, a cluster asking for seven in
     * the morning in Berlin was written to at nine in summer and eight in winter.
     *
     * @param stationIds the stations to read
     * @param clusterIds the clusters to read
     * @return one group for each that exists
     */
    public List<DigestGroup> findDigestGroups(Collection<Integer> stationIds, Collection<Integer> clusterIds) {
        return query("""
                SELECT 'STATION' AS kind, s.id, s.name, s.uid AS station_uid, s.id AS clock_station_id,
                       s.timezone, s.locale, s.notification_send_times, s.notification_last_sent
                FROM station s
                WHERE s.id = ANY(:station_ids::INT[])
                UNION ALL
                SELECT 'CLUSTER', c.id, c.name, NULL::UUID, home.id,
                       home.timezone, home.locale, c.notification_send_times, c.notification_last_sent
                FROM cluster c
                JOIN station home ON home.id = c.home_station_id
                WHERE c.id = ANY(:cluster_ids::INT[]);""")
                .single(call().bind("station_ids", List.copyOf(stationIds), PostgreSqlTypes.INTEGER)
                        .bind("cluster_ids", List.copyOf(clusterIds), PostgreSqlTypes.INTEGER))
                .map(row -> new DigestGroup(
                        new DigestGroup.Key(row.getEnum("kind", DigestGroup.Kind.class), row.getInt("id")),
                        row.getString("name"),
                        row.get("station_uid", UUID_STRING),
                        StationFormat.timezoneOf(row.getInt("clock_station_id"), row.getString("timezone")),
                        row.getString("locale"),
                        timesOf(row.getObject("notification_send_times", Array.class)),
                        row.get("notification_last_sent", INSTANT_TIMESTAMP)))
                .all();
    }

    public void markStationSent(int stationId, Instant sentAt) {
        query("UPDATE station SET notification_last_sent = :sent WHERE id = :id;")
                .single(call().bind("sent", sentAt, INSTANT_TIMESTAMP).bind("id", stationId))
                .update();
    }

    public void markClusterSent(int clusterId, Instant sentAt) {
        query("UPDATE cluster SET notification_last_sent = :sent WHERE id = :id;")
                .single(call().bind("sent", sentAt, INSTANT_TIMESTAMP).bind("id", clusterId))
                .update();
    }

    /**
     * Writes the times a station asked for, or clears them so the operator's number decides again.
     */
    public void setStationSendTimes(int stationId, List<LocalTime> sendTimes) {
        query("UPDATE station SET notification_send_times = :times::time[] WHERE id = :id;")
                .single(call().bind("times", literalOf(sendTimes)).bind("id", stationId))
                .update();
    }

    public void setClusterSendTimes(int clusterId, List<LocalTime> sendTimes) {
        query("UPDATE cluster SET notification_send_times = :times::time[] WHERE id = :id;")
                .single(call().bind("times", literalOf(sendTimes)).bind("id", clusterId))
                .update();
    }

    /**
     * The array as postgres takes it, or null where nothing was asked for.
     *
     * <p>An empty list and no list at all mean the same thing here, which is that the operator's
     * number decides, so both are stored as nothing rather than as an array holding nothing.
     */
    private static @Nullable String literalOf(List<LocalTime> sendTimes) {
        if (sendTimes == null || sendTimes.isEmpty()) return null;
        var parts = new ArrayList<String>();
        for (LocalTime time : sendTimes.stream().sorted().distinct().toList()) {
            parts.add(time.toString());
        }
        return "{" + String.join(",", parts) + "}";
    }

    /**
     * The times an array column holds.
     *
     * <p>A failure to read it is left to travel rather than answered with no times: a station that
     * asked for two would otherwise be read as having asked for none, and be written to on the
     * operator's number without anybody being told why.
     */
    private static List<LocalTime> timesOf(Array array) throws SQLException {
        if (array == null) return List.of();
        var times = new ArrayList<LocalTime>();
        for (Object value : (Object[]) array.getArray()) {
            if (value instanceof Time time) times.add(time.toLocalTime());
            else if (value != null) times.add(LocalTime.parse(value.toString()));
        }
        return List.copyOf(times);
    }
}
