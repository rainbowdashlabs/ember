/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the reminder tables: {@code event_reminder} holds how many days before an
 * occurrence a reminder goes out, {@code event_reminder_sent} records which of those reminders
 * already went out so an occurrence is never announced twice.
 */
@Singleton
public class EventReminderRepository {

    /**
     * Retrieves the configured reminder lead times of an event, ascending.
     *
     * @param eventId the event ID
     * @return the days-before values
     */
    public List<Integer> findDays(int eventId) {
        return query("SELECT days_before FROM event_reminder WHERE event_id = :event_id ORDER BY days_before;")
                .single(call().bind("event_id", eventId))
                .map(row -> row.getInt("days_before"))
                .all();
    }

    /**
     * Replaces an event's reminder lead times with the given ones, dropping duplicates.
     *
     * @param eventId    the event ID
     * @param daysBefore the new days-before values
     */
    public void replace(int eventId, List<Integer> daysBefore) {
        query("DELETE FROM event_reminder WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
        for (int days : daysBefore) {
            query("""
                    INSERT
                    INTO
                        event_reminder(event_id, days_before)
                    VALUES
                        (:event_id, :days_before)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("event_id", eventId).bind("days_before", days))
                    .insert();
        }
    }

    /**
     * Reports whether a reminder already went out for an occurrence and lead time.
     *
     * @param eventId    the event ID
     * @param eventDate  the occurrence date
     * @param daysBefore the reminder lead time
     */
    /** Whether the run-out warning for this lead time has already gone out for an event. */
    public boolean isDeadlineWarningSent(int eventId, int daysBefore) {
        return SqlSupport.exists("""
                SELECT
                    1
                FROM
                    event_deadline_reminder_sent
                WHERE event_id = :event_id
                  AND days_before = :days_before;""", call().bind("event_id", eventId).bind("days_before", daysBefore));
    }

    /** Records that the run-out warning for this lead time has gone out. */
    public void markDeadlineWarningSent(int eventId, int daysBefore) {
        query("""
                INSERT
                INTO
                    event_deadline_reminder_sent(event_id, days_before)
                VALUES
                    (:event_id, :days_before)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("event_id", eventId).bind("days_before", daysBefore))
                .insert();
    }

    public boolean isSent(int eventId, LocalDate eventDate, int daysBefore) {
        return SqlSupport.exists(
                """
                SELECT
                    1
                FROM
                    event_reminder_sent
                WHERE event_id = :event_id
                  AND event_date = :event_date
                  AND days_before = :days_before;""",
                call().bind("event_id", eventId).bind("event_date", eventDate).bind("days_before", daysBefore));
    }

    /**
     * Records that a reminder went out for an occurrence and lead time.
     *
     * @param eventId    the event ID
     * @param eventDate  the occurrence date
     * @param daysBefore the reminder lead time
     */
    public void markSent(int eventId, LocalDate eventDate, int daysBefore) {
        query("""
                INSERT
                INTO
                    event_reminder_sent(event_id, event_date, days_before)
                VALUES
                    (:event_id, :event_date, :days_before)
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("event_id", eventId)
                        .bind("event_date", eventDate)
                        .bind("days_before", daysBefore))
                .insert();
    }
}
