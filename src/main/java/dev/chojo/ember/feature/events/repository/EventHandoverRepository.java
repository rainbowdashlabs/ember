/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.events.entity.DateSpan;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Hands an appointment's settings and the records filed against some of its dates to another
 * appointment that takes those dates over: a date of a series that became an appointment of its own,
 * or the later dates of a series that goes on as a new one.
 *
 * <p>Settings are copied, so both appointments keep them. What is filed against a date (places and
 * their answers, the answers a question carries for the day, a cancellation, a reminder sent,
 * documents asked for and handed in, generated documents, procedures, agreements, attendance sheets,
 * equipment, lending requests, checklists, trial dates of the waiting list, and dates the series
 * already gave away) is moved, because the date now belongs to the other appointment. Comments stay
 * where they were written.
 */
@Singleton
public class EventHandoverRepository {

    /** Tables whose rows name an appointment and one of its dates, as table, event column, date column. */
    private static final List<DatedTable> DATED_TABLES = List.of(
            new DatedTable("event_registration", "event_id", "event_date"),
            new DatedTable("event_date_cancellation", "event_id", "event_date"),
            new DatedTable("event_reminder_sent", "event_id", "event_date"),
            new DatedTable("event_document_submission", "event_id", "event_date"),
            new DatedTable("document_generation", "event_id", "event_date"),
            new DatedTable("procedure", "event_id", "event_date"),
            new DatedTable("event_partner_agreement", "event_id", "event_date"),
            new DatedTable("partner_agreement", "event_id", "event_date"),
            new DatedTable("waiting_list_invitation_option", "event_id", "event_date"),
            new DatedTable("federation_lending_request", "event_id", "event_date"),
            new DatedTable("event_equipment_need", "event_id", "event_date"),
            new DatedTable("checklist", "source_event_id", "source_event_date"),
            new DatedTable("station_event_detached_date", "event_id", "event_date"));

    /**
     * Copies everything an appointment is set up with to another one: both audiences and how they
     * combine, the reminders, the questions and their values, the registration questions, the
     * defaults of the attendance sheet, the files, the documents to bring and the equipment every
     * date needs.
     *
     * @param source the appointment copied from
     * @param target the appointment copied to, which has none of these yet
     */
    public void copyConfiguration(int source, int target) {
        var ids = call().bind("source", source).bind("target", target);
        for (var statement : List.of("""
                UPDATE station_event t
                SET restriction_mode      = s.restriction_mode,
                    view_restriction_mode = s.view_restriction_mode
                FROM station_event s
                WHERE s.id = :source
                  AND t.id = :target;""", """
                INSERT INTO event_restriction(event_id, group_id, tag_id, member_id, user_type)
                SELECT :target, group_id, tag_id, member_id, user_type
                FROM event_restriction
                WHERE event_id = :source
                ORDER BY id;""", """
                INSERT INTO event_view_restriction(event_id, group_id, tag_id, member_id, user_type)
                SELECT :target, group_id, tag_id, member_id, user_type
                FROM event_view_restriction
                WHERE event_id = :source
                ORDER BY id;""", """
                INSERT INTO event_reminder(event_id, days_before)
                SELECT :target, days_before
                FROM event_reminder
                WHERE event_id = :source
                ORDER BY id;""", """
                INSERT INTO event_field(event_id, name, value, position, field_type, config, overview, attendance_field_id, public)
                SELECT :target, name, value, position, field_type, config, overview, attendance_field_id, public
                FROM event_field
                WHERE event_id = :source
                ORDER BY position, id;""", """
                INSERT INTO event_registration_field(event_id, name, field_type, config, position, overview)
                SELECT :target, name, field_type, config, position, overview
                FROM event_registration_field
                WHERE event_id = :source
                ORDER BY position, id;""", """
                INSERT INTO event_field_default(event_id, field_id, source, value)
                SELECT :target, field_id, source, value
                FROM event_field_default
                WHERE event_id = :source;""", """
                INSERT INTO event_attachment(event_id, file_id, label, internal, sort_order)
                SELECT :target, file_id, label, internal, sort_order
                FROM event_attachment
                WHERE event_id = :source
                ORDER BY sort_order, id;""", """
                INSERT INTO event_document_requirement(event_id, template_id, position)
                SELECT :target, template_id, position
                FROM event_document_requirement
                WHERE event_id = :source
                ORDER BY position, id;""", """
                INSERT INTO event_equipment_need(event_id, event_date, item_id, art_id, inventory_id, quantity, lead_minutes, trail_minutes, position)
                SELECT :target, NULL, item_id, art_id, inventory_id, quantity, lead_minutes, trail_minutes, position
                FROM event_equipment_need
                WHERE event_id = :source
                  AND event_date IS NULL
                ORDER BY position, id;""")) {
            query(statement).single(ids).update();
        }
    }

    /**
     * Moves what is filed against some dates of one appointment to the appointment that took them
     * over, which carries a copy of its settings by now.
     *
     * <p>An answer belongs to a question, and a question was copied rather than moved, so the answers
     * filed against the moved dates are put on the copy of their question: the one at the same place
     * with the same name. Equipment handed over for a moved date goes to the copy of the need it was
     * handed over for in the same way.
     *
     * @param source the appointment the dates were taken from
     * @param target the appointment that holds them now
     * @param span   the dates
     */
    public void moveDates(int source, int target, DateSpan span) {
        moveAnswersOfQuestions(source, target, span);
        moveHandoversOfSeriesNeeds(source, target, span);
        for (var table : DATED_TABLES) {
            query("""
                    UPDATE %s
                    SET %s = :target
                    WHERE %s = :source
                      AND %s;""", table.name(), table.eventColumn(), table.eventColumn(), within(table.dateColumn(), span))
                    .single(bound(call().bind("source", source).bind("target", target), span))
                    .update();
        }
        moveAnswersOfRegistrations(source, target, span);
    }

    /**
     * Moves the attendance sheets of an appointment that start within a window to the appointment that
     * took those dates over. A sheet carries the moment it starts rather than a date.
     *
     * @param source the appointment the dates were taken from
     * @param target the appointment that holds them now
     * @param from   the first moment of the first date, on the station's clock
     * @param until  the first moment after the last date, or null where every later sheet moves
     */
    public void moveSheets(int source, int target, Instant from, @Nullable Instant until) {
        query("""
                UPDATE attendance_session
                SET event_id = :target
                WHERE event_id = :source
                  AND start_time >= :from
                  %s;""", until == null ? "" : "AND start_time < :until")
                .single(sheetWindow(call().bind("source", source).bind("target", target), from, until))
                .update();
    }

    private static Call sheetWindow(Call call, Instant from, @Nullable Instant until) {
        var bound = call.bind("from", from, INSTANT_TIMESTAMP);
        return until == null ? bound : bound.bind("until", until, INSTANT_TIMESTAMP);
    }

    private static void moveAnswersOfQuestions(int source, int target, DateSpan span) {
        query("""
                UPDATE event_field_date_value v
                SET field_id = copy.id
                FROM event_field original
                         JOIN event_field copy
                              ON copy.event_id = :target
                                  AND copy.position = original.position
                                  AND copy.name = original.name
                WHERE original.id = v.field_id
                  AND original.event_id = :source
                  AND %s;""", within("v.event_date", span))
                .single(bound(call().bind("source", source).bind("target", target), span))
                .update();
    }

    private static void moveHandoversOfSeriesNeeds(int source, int target, DateSpan span) {
        query("""
                UPDATE event_equipment_handover h
                SET need_id = copy.id
                FROM event_equipment_need original
                         JOIN event_equipment_need copy
                              ON copy.event_id = :target
                                  AND copy.event_date IS NULL
                                  AND copy.position = original.position
                                  AND copy.item_id IS NOT DISTINCT FROM original.item_id
                                  AND copy.art_id IS NOT DISTINCT FROM original.art_id
                                  AND copy.inventory_id IS NOT DISTINCT FROM original.inventory_id
                WHERE original.id = h.need_id
                  AND original.event_id = :source
                  AND original.event_date IS NULL
                  AND %s;""", within("h.event_date", span))
                .single(bound(call().bind("source", source).bind("target", target), span))
                .update();
    }

    private static void moveAnswersOfRegistrations(int source, int target, DateSpan span) {
        query("""
                UPDATE event_registration_field_value v
                SET field_id = copy.id
                FROM event_registration r,
                     event_registration_field original
                         JOIN event_registration_field copy
                              ON copy.event_id = :target
                                  AND copy.position = original.position
                                  AND copy.name = original.name
                WHERE r.id = v.registration_id
                  AND r.event_id = :target
                  AND %s
                  AND original.id = v.field_id
                  AND original.event_id = :source;""", within("r.event_date", span))
                .single(bound(call().bind("source", source).bind("target", target), span))
                .update();
    }

    private static String within(String dateColumn, DateSpan span) {
        String from = dateColumn + " >= :span_from";
        return span.until() == null ? from : from + " AND " + dateColumn + " <= :span_until";
    }

    private static Call bound(Call call, DateSpan span) {
        var bound = call.bind("span_from", span.from());
        var until = span.until();
        return until == null ? bound : bound.bind("span_until", until);
    }

    /**
     * A table whose rows name an appointment and one of its dates.
     *
     * @param name        the table
     * @param eventColumn the column naming the appointment
     * @param dateColumn  the column naming the date
     */
    private record DatedTable(String name, String eventColumn, String dateColumn) {}
}
