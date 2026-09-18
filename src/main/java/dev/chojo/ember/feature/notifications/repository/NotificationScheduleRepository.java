/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.repository;

import jakarta.inject.Singleton;

import java.sql.Array;
import java.sql.Time;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

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
     * What a station or cluster asked for.
     *
     * @param sendTimes the times of day it wants its mail, empty where it asked for none and the
     *     operator's own number decides
     * @param lastSent  when it was last written to, null where it never has been
     * @param timezone  the clock its times are read on, which for a cluster is the instance's
     */
    public record Schedule(List<LocalTime> sendTimes, Instant lastSent, String timezone) {}

    public Optional<Schedule> forStation(int stationId) {
        return query("""
                SELECT notification_send_times, notification_last_sent, timezone
                FROM station WHERE id = :id;""")
                .single(call().bind("id", stationId))
                .map(row -> new Schedule(
                        timesOf(row.getObject("notification_send_times", Array.class)),
                        row.get("notification_last_sent", INSTANT_TIMESTAMP),
                        row.getString("timezone")))
                .first();
    }

    public Optional<Schedule> forCluster(int clusterId) {
        return query("""
                SELECT notification_send_times, notification_last_sent
                FROM cluster WHERE id = :id;""")
                .single(call().bind("id", clusterId))
                .map(row -> new Schedule(
                        timesOf(row.getObject("notification_send_times", Array.class)),
                        row.get("notification_last_sent", INSTANT_TIMESTAMP),
                        null))
                .first();
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
    private static String literalOf(List<LocalTime> sendTimes) {
        if (sendTimes == null || sendTimes.isEmpty()) return null;
        var parts = new ArrayList<String>();
        for (LocalTime time : sendTimes.stream().sorted().distinct().toList()) {
            parts.add(time.toString());
        }
        return "{" + String.join(",", parts) + "}";
    }

    private static List<LocalTime> timesOf(Array array) {
        if (array == null) return List.of();
        try {
            Object raw = array.getArray();
            if (!(raw instanceof Object[] values)) return List.of();
            var times = new ArrayList<LocalTime>();
            for (Object value : values) {
                if (value instanceof Time time) times.add(time.toLocalTime());
                else if (value != null) times.add(LocalTime.parse(value.toString()));
            }
            return List.copyOf(times);
        } catch (Exception e) {
            return List.of();
        }
    }
}
