/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

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
     * The reminder lead times that take effect for an appointment, ascending: its own, or for a part of
     * an event group without reminders of its own the event group's.
     *
     * @param eventId the appointment
     * @return the days-before values
     */
    public List<Integer> findEffectiveDays(int eventId) {
        return query("""
                SELECT days_before
                FROM event_reminder
                WHERE event_id = :event_id
                UNION ALL
                SELECT gr.days_before
                FROM event_group_reminder gr
                         JOIN event_group_set gs ON gs.event_group_id = gr.event_group_id
                         JOIN station_event e ON e.event_group_set_id = gs.id
                WHERE e.id = :event_id
                  AND NOT EXISTS (SELECT 1 FROM event_reminder own WHERE own.event_id = :event_id)
                ORDER BY days_before;""")
                .single(call().bind("event_id", eventId))
                .map(row -> row.getInt("days_before"))
                .all();
    }

    /**
     * The reminder lead times an event group hands to its parts, ascending.
     *
     * @param eventGroupId the event group
     * @return the days-before values
     */
    public List<Integer> findEventGroupDays(int eventGroupId) {
        return query(
                        "SELECT days_before FROM event_group_reminder WHERE event_group_id = :event_group_id ORDER BY days_before;")
                .single(call().bind("event_group_id", eventGroupId))
                .map(row -> row.getInt("days_before"))
                .all();
    }

    /**
     * Replaces the reminder lead times an event group hands to its parts, dropping duplicates.
     *
     * @param eventGroupId the event group
     * @param daysBefore   the new days-before values, each above zero
     */
    public void replaceEventGroupDays(int eventGroupId, List<Integer> daysBefore) {
        query("DELETE FROM event_group_reminder WHERE event_group_id = :event_group_id;")
                .single(call().bind("event_group_id", eventGroupId))
                .delete();
        query("""
                INSERT INTO event_group_reminder(event_group_id, days_before)
                SELECT DISTINCT :event_group_id, days
                FROM unnest(:days::INT[]) AS chosen(days);""")
                .single(call().bind("event_group_id", eventGroupId).bind("days", daysBefore, PostgreSqlTypes.INTEGER))
                .insert();
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
     * The closing warnings that went out for upcoming dates of the given appointments.
     *
     * @param eventIds the appointments
     * @return one entry per appointment, date and lead time warned about
     */
    public List<DeadlineWarning> findDeadlineWarningsSent(Collection<Integer> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        return query("""
                SELECT event_id, occurrence_date, days_before, closes_at
                FROM event_deadline_reminder_sent
                WHERE event_id = ANY (:event_ids)
                  AND occurrence_date >= current_date - 1;""")
                .single(call().bind("event_ids", List.copyOf(eventIds), PostgreSqlTypes.INTEGER))
                .map(row -> new DeadlineWarning(
                        row.getInt("event_id"),
                        row.getObject("occurrence_date", LocalDate.class),
                        row.getInt("days_before"),
                        row.get("closes_at", INSTANT_TIMESTAMP)))
                .all();
    }

    /**
     * A closing warning that went out.
     *
     * @param eventId    the appointment
     * @param date       the date it was about
     * @param daysBefore the lead time
     * @param closesAt   the close it warned about; a warning about an earlier close no longer counts
     */
    public record DeadlineWarning(int eventId, LocalDate date, int daysBefore, Instant closesAt) {}

    /**
     * Records that the closing warning for a lead time has gone out for one date of an appointment
     * closing at an instant, replacing a warning about an earlier close.
     *
     * @param eventId    the appointment
     * @param date       the date whose deadline was warned about
     * @param daysBefore the lead time
     * @param closesAt   the close warned about
     */
    public void markDeadlineWarningSent(int eventId, LocalDate date, int daysBefore, Instant closesAt) {
        query("""
                INSERT
                INTO
                    event_deadline_reminder_sent(event_id, occurrence_date, days_before, closes_at)
                VALUES
                    (:event_id, :date, :days_before, :closes_at)
                ON CONFLICT (event_id, occurrence_date, days_before)
                    DO UPDATE SET closes_at = excluded.closes_at, sent_at = now();""")
                .single(call().bind("event_id", eventId)
                        .bind("date", date)
                        .bind("days_before", daysBefore)
                        .bind("closes_at", closesAt, INSTANT_TIMESTAMP))
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
     * The dates of an appointment, from one day on, that a reminder has already gone out for.
     *
     * @param eventId the appointment
     * @param from    the first date asked about
     * @return the dates, in no particular order
     */
    public List<LocalDate> findSentDatesFrom(int eventId, LocalDate from) {
        return query("""
                SELECT DISTINCT event_date
                FROM event_reminder_sent
                WHERE event_id = :event_id
                  AND event_date >= :from;""")
                .single(call().bind("event_id", eventId).bind("from", from))
                .map(row -> row.getObject("event_date", LocalDate.class))
                .all();
    }

    /**
     * Forgets the reminders sent for dates an appointment no longer falls on, so nothing about them
     * is kept for a day that is not an occasion any more.
     *
     * @param eventId the appointment
     * @param dates   the dates it no longer falls on
     */
    public void deleteSentOn(int eventId, Collection<LocalDate> dates) {
        if (dates.isEmpty()) return;
        query("""
                DELETE FROM event_reminder_sent
                WHERE event_id = :event_id
                  AND event_date = ANY(:dates);""")
                .single(call().bind("event_id", eventId).bind("dates", List.copyOf(dates), PostgreSqlTypes.DATE))
                .delete();
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
