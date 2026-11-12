/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventDetachedDate;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code station_event_detached_date} table: the dates a series gave away to an
 * appointment of their own.
 */
@Singleton
public class EventDetachedDateRepository {

    /**
     * Takes one date out of a series. Taking out a date already taken out changes nothing.
     *
     * @param eventId the series
     * @param date    the date
     * @return true where the date was still part of the series
     */
    public boolean detach(int eventId, LocalDate date) {
        return query("""
                INSERT INTO station_event_detached_date(event_id, event_date)
                VALUES (:event_id, :event_date)
                ON CONFLICT (event_id, event_date) DO NOTHING;""")
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .insert()
                .changed();
    }

    /**
     * Whether this date was taken out of this series.
     *
     * @param eventId the series
     * @param date    the date
     * @return true where it became an appointment of its own
     */
    public boolean isDetached(int eventId, LocalDate date) {
        return query("""
                SELECT EXISTS(SELECT 1
                              FROM station_event_detached_date
                              WHERE event_id = :event_id
                                AND event_date = :event_date) AS detached;""")
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(row -> row.getBoolean("detached"))
                .first()
                .orElse(false);
    }

    /**
     * The dates taken out of one series.
     *
     * @param eventId the series
     * @return those dates, earliest first
     */
    public List<EventDetachedDate> findByEvent(int eventId) {
        return query("""
                SELECT event_id, event_date
                FROM station_event_detached_date
                WHERE event_id = :event_id
                ORDER BY event_date;""")
                .single(call().bind("event_id", eventId))
                .map(EventDetachedDate.map())
                .all();
    }

    /**
     * The dates taken out of every series of a station, which the station's calendar skips.
     *
     * @param stationId the station
     * @return those dates, in no particular order
     */
    public List<EventDetachedDate> findByStation(int stationId) {
        return query("""
                SELECT d.event_id, d.event_date
                FROM station_event_detached_date d
                         JOIN station_event e ON e.id = d.event_id
                WHERE e.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(EventDetachedDate.map())
                .all();
    }
}
