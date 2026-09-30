/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code event_date_cancellation} table: the dates of appointments that were
 * called off, and brought back.
 */
@Singleton
public class EventDateCancellationRepository {

    /**
     * The condition, for a query reading {@code station_event} as {@code e}, that no date of the event
     * is off. It is what the reads about one-time appointments ask, whose one date is the event.
     */
    public static final String NO_DATE_CANCELLED = """
            NOT EXISTS (SELECT 1
                        FROM event_date_cancellation edc
                        WHERE edc.event_id = e.id
                          AND edc.restored_at IS NULL)""";

    private static final String COLUMNS =
            "event_id, event_date, cause, reason, cancelled_at, cancelled_by, restored_at";

    /**
     * Calls one date off, or calls a restored one off again with the new cause and reason.
     *
     * @param eventId     the appointment
     * @param date        the date
     * @param cause       who calls it off
     * @param reason      the reason given, or null
     * @param cancelledBy the manager calling it off, or null for the check
     * @return true where the date was not off before and is now
     */
    public boolean cancel(int eventId, LocalDate date, CancellationCause cause, String reason, Integer cancelledBy) {
        return query("""
                INSERT INTO event_date_cancellation(event_id, event_date, cause, reason, cancelled_by)
                VALUES (:event_id, :event_date, :cause, :reason, :cancelled_by)
                ON CONFLICT (event_id, event_date) DO UPDATE
                    SET cause        = excluded.cause,
                        reason       = excluded.reason,
                        cancelled_by = excluded.cancelled_by,
                        cancelled_at = now(),
                        restored_at  = NULL
                    WHERE event_date_cancellation.restored_at IS NOT NULL;""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", date)
                        .bind("cause", cause)
                        .bind("reason", reason)
                        .bind("cancelled_by", cancelledBy))
                .insert()
                .changed();
    }

    /**
     * Calls a date off for too few registrations, unless it was ever called off before.
     *
     * <p>A date that has a row at all is left alone: either it is off already, or a manager restored
     * it, and a restored date was brought back on purpose.
     *
     * @param eventId the appointment
     * @param date    the date
     * @return true where the date is now off and was never off before
     */
    public boolean cancelForTooFewRegistrations(int eventId, LocalDate date) {
        return query("""
                INSERT INTO event_date_cancellation(event_id, event_date, cause)
                VALUES (:event_id, :event_date, :cause)
                ON CONFLICT (event_id, event_date) DO NOTHING;""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", date)
                        .bind("cause", CancellationCause.THRESHOLD))
                .insert()
                .changed();
    }

    /**
     * Brings a date that is off back.
     *
     * @param eventId the appointment
     * @param date    the date
     * @return true where the date was off and no longer is
     */
    public boolean restore(int eventId, LocalDate date) {
        return query("""
                UPDATE event_date_cancellation
                SET restored_at = now()
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND restored_at IS NULL;""")
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .update()
                .changed();
    }

    /**
     * The row of one date, off or restored.
     *
     * @param eventId the appointment
     * @param date    the date
     * @return the row, empty where the date was never called off
     */
    public Optional<EventDateCancellation> find(int eventId, LocalDate date) {
        return query("""
                SELECT %s
                FROM event_date_cancellation
                WHERE event_id = :event_id
                  AND event_date = :event_date;""", COLUMNS)
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(EventDateCancellation.map())
                .first();
    }

    /**
     * Whether one date of an appointment is off.
     *
     * @param eventId the appointment
     * @param date    the date
     * @return true while the date is off
     */
    public boolean isCancelled(int eventId, LocalDate date) {
        return SqlSupport.exists("""
                SELECT 1
                FROM event_date_cancellation
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND restored_at IS NULL;""", call().bind("event_id", eventId).bind("event_date", date));
    }

    /**
     * The dates of one appointment that are off.
     *
     * @param eventId the appointment
     * @return those dates, earliest first
     */
    public List<EventDateCancellation> findActiveByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_date_cancellation
                WHERE event_id = :event_id
                  AND restored_at IS NULL
                ORDER BY event_date;""", COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventDateCancellation.map())
                .all();
    }

    /**
     * The dates of every appointment of a station that are off, which is what the station's calendar
     * holds beside its breaks.
     *
     * @param stationId the station
     * @return those dates, in no particular order
     */
    public List<EventDateCancellation> findActiveByStation(int stationId) {
        return query("""
                SELECT %s
                FROM event_date_cancellation c
                         JOIN station_event e ON e.id = c.event_id
                WHERE e.station_id = :station_id
                  AND c.restored_at IS NULL;""", SqlSupport.alias("c", COLUMNS))
                .single(call().bind("station_id", stationId))
                .map(EventDateCancellation.map())
                .all();
    }

    /**
     * Carries what a one-time appointment's date says about being off to the date it moved to, since
     * it is the same occasion.
     *
     * @param eventId the appointment
     * @param from    the date it fell on
     * @param to      the date it falls on now
     */
    public void moveDate(int eventId, LocalDate from, LocalDate to) {
        query("""
                UPDATE event_date_cancellation
                SET event_date = :to
                WHERE event_id = :event_id
                  AND event_date = :from;""")
                .single(call().bind("event_id", eventId).bind("from", from).bind("to", to))
                .update();
    }

    /**
     * Forgets the rows of dates an appointment no longer falls on.
     *
     * @param eventId the appointment
     * @param dates   the dates it left
     */
    public void deleteOn(int eventId, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return;
        query("""
                DELETE FROM event_date_cancellation
                WHERE event_id = :event_id
                  AND event_date = ANY(:dates);""")
                .single(call().bind("event_id", eventId).bind("dates", List.copyOf(dates), PostgreSqlTypes.DATE))
                .delete();
    }
}
