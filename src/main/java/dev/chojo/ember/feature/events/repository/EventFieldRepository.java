/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventFieldRepository {

    private static final String EVENT_FIELD_COLUMNS =
            "id, event_id, name, field_type, config, value, position, overview, attendance_field_id, \"public\"";

    /**
     * The same columns read for one date of the appointment, with the answer that date carries.
     *
     * <p>A question answered per date keeps nothing in {@code event_field.value}, so it falls back to
     * empty rather than to the appointment's own answer: an unanswered date is unanswered, and
     * showing the answer of some other date there would be a lie the reader cannot see through.
     */
    private static final String EVENT_FIELD_COLUMNS_ON_DATE = """
            ef.id, ef.event_id, ef.name, ef.field_type, ef.config, ef.position, ef.overview,
            ef.attendance_field_id, ef."public",
            CASE WHEN (ef.config ->> 'perDate')::BOOLEAN IS TRUE THEN COALESCE(dv.value, '') ELSE ef.value END AS value""";

    private static final String ON_DATE_JOIN = """
            FROM event_field ef
            LEFT JOIN event_field_date_value dv ON dv.field_id = ef.id AND dv.event_date = :event_date""";

    public Optional<AppointmentField> findById(int fieldId) {
        return SqlSupport.findById("event_field", EVENT_FIELD_COLUMNS, fieldId, AppointmentField.map());
    }

    /** One question as it stands on one date of the appointment. */
    public Optional<AppointmentField> findByIdOn(int fieldId, LocalDate date) {
        return query("""
                SELECT %s
                %s
                WHERE ef.id = :id;""", EVENT_FIELD_COLUMNS_ON_DATE, ON_DATE_JOIN)
                .single(call().bind("id", fieldId).bind("event_date", date))
                .map(AppointmentField.map())
                .first();
    }

    public void updateValue(int fieldId, String value) {
        query("""
                UPDATE event_field
                SET value = :value
                WHERE id = :id;""").single(call().bind("value", value).bind("id", fieldId)).update();
    }

    /** Writes the answer one question carries on one date, replacing whatever stood there. */
    public void updateValueOn(int fieldId, LocalDate date, String value) {
        query("""
                INSERT INTO event_field_date_value(field_id, event_date, value)
                VALUES (:field_id, :event_date, :value)
                ON CONFLICT (field_id, event_date) DO UPDATE SET value = EXCLUDED.value;""")
                .single(call().bind("field_id", fieldId)
                        .bind("event_date", date)
                        .bind("value", value))
                .insert();
    }

    public List<AppointmentField> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_field
                WHERE event_id = :event_id
                ORDER BY position;""", EVENT_FIELD_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(AppointmentField.map())
                .all();
    }

    /** Every question of an appointment as it stands on one of its dates. */
    public List<AppointmentField> findByEventOn(int eventId, LocalDate date) {
        return query("""
                SELECT %s
                %s
                WHERE ef.event_id = :event_id
                ORDER BY ef.position;""", EVENT_FIELD_COLUMNS_ON_DATE, ON_DATE_JOIN)
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(AppointmentField.map())
                .all();
    }

    public List<AppointmentField> findOverviewFieldsByEvents(List<Integer> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM event_field
                WHERE event_id = ANY(:event_ids) AND overview
                ORDER BY event_id, position;""", EVENT_FIELD_COLUMNS)
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER))
                .map(AppointmentField.map())
                .all();
    }

    /**
     * The questions marked for the overview, each as it stands on one date of its own appointment.
     *
     * <p>Every appointment on a list of them is drawn on a different day, so one date for the whole
     * query would answer most of them wrongly. The dates arrive as two lists read in step, which is
     * how a set of pairs travels into a single statement.
     */
    public List<AppointmentField> findOverviewFieldsByEventsOn(List<Integer> eventIds, List<LocalDate> dates) {
        if (eventIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM event_field ef
                JOIN UNNEST(:event_ids::INTEGER[], :dates::DATE[]) AS asked(event_id, event_date)
                     ON asked.event_id = ef.event_id
                LEFT JOIN event_field_date_value dv ON dv.field_id = ef.id AND dv.event_date = asked.event_date
                WHERE ef.overview
                ORDER BY ef.event_id, ef.position;""", EVENT_FIELD_COLUMNS_ON_DATE)
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER)
                        .bind("dates", dates, PostgreSqlTypes.DATE))
                .map(AppointmentField.map())
                .all();
    }

    /**
     * Every answer an appointment's questions carry per date, as a value per question and date.
     *
     * @param eventId the appointment
     * @return the answers, keyed by question and then by date
     */
    public Map<Integer, Map<LocalDate, String>> findDateValues(int eventId) {
        var values = new HashMap<Integer, Map<LocalDate, String>>();
        query("""
                SELECT dv.field_id, dv.event_date, dv.value
                FROM event_field_date_value dv
                JOIN event_field ef ON ef.id = dv.field_id
                WHERE ef.event_id = :event_id;""")
                .single(call().bind("event_id", eventId))
                .map(row -> new DateValue(
                        row.getInt("field_id"), row.getObject("event_date", LocalDate.class), row.getString("value")))
                .all()
                .forEach(entry -> values.computeIfAbsent(entry.fieldId(), _ -> new HashMap<>())
                        .put(entry.date(), entry.value()));
        return values;
    }

    /**
     * The dates of an appointment, from one day on, that one of its questions carries an answer for.
     *
     * @param eventId the appointment
     * @param from    the first date asked about
     * @return the dates, in no particular order
     */
    public List<LocalDate> findDateValueDatesFrom(int eventId, LocalDate from) {
        return query("""
                SELECT DISTINCT dv.event_date
                FROM event_field_date_value dv
                JOIN event_field ef ON ef.id = dv.field_id
                WHERE ef.event_id = :event_id
                  AND dv.event_date >= :from;""")
                .single(call().bind("event_id", eventId).bind("from", from))
                .map(row -> row.getObject("event_date", LocalDate.class))
                .all();
    }

    /**
     * Drops the answers an appointment's questions carry for dates it no longer falls on. Nobody can
     * reach them any more, and a question naming members would otherwise put the members back on a
     * day that is gone.
     *
     * @param eventId the appointment
     * @param dates   the dates it no longer falls on
     */
    public void deleteDateValuesOn(int eventId, List<LocalDate> dates) {
        if (dates.isEmpty()) return;
        query("""
                DELETE FROM event_field_date_value dv
                USING event_field ef
                WHERE ef.id = dv.field_id
                  AND ef.event_id = :event_id
                  AND dv.event_date = ANY(:dates);""")
                .single(call().bind("event_id", eventId).bind("dates", dates, PostgreSqlTypes.DATE))
                .delete();
    }

    /**
     * Carries the per-date answers of a one-off appointment from the day it left to the day it now
     * falls on. An answer already written for the new day is kept, and the old one stays behind.
     *
     * @param eventId the appointment
     * @param from    the day it fell on
     * @param to      the day it falls on now
     */
    public void moveDateValues(int eventId, LocalDate from, LocalDate to) {
        query("""
                UPDATE event_field_date_value dv
                SET event_date = :to
                FROM event_field ef
                WHERE ef.id = dv.field_id
                  AND ef.event_id = :event_id
                  AND dv.event_date = :from
                  AND NOT EXISTS (SELECT 1
                                  FROM event_field_date_value taken
                                  WHERE taken.field_id = dv.field_id
                                    AND taken.event_date = :to);""")
                .single(call().bind("event_id", eventId).bind("from", from).bind("to", to))
                .update();
    }

    /** The appointments that ask at least one question naming members. */
    public List<Integer> findEventIdsWithMemberFields() {
        var memberTypes = Arrays.stream(FieldType.values())
                .filter(FieldType::namesMembers)
                .map(FieldType::name)
                .toList();
        return query("""
                SELECT DISTINCT event_id
                FROM event_field
                WHERE field_type = ANY(:types);""")
                .single(call().bind("types", memberTypes, PostgreSqlTypes.TEXT))
                .map(row -> row.getInt("event_id"))
                .all();
    }

    private record DateValue(int fieldId, LocalDate date, String value) {}

    public List<String> findDistinctFieldNames(int stationId) {
        return query("""
                SELECT name
                FROM (SELECT ef.name
                      FROM event_field ef
                          JOIN station_event se ON se.id = ef.event_id
                      WHERE se.station_id = :station_id
                      UNION
                      SELECT f.name
                      FROM event_group_field f
                          JOIN event_group g ON g.id = f.group_id
                      WHERE g.station_id = :station_id) names
                ORDER BY name;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString("name"))
                .all();
    }

    public AppointmentField create(
            int eventId,
            String name,
            FieldType fieldType,
            EventQuestionSettings config,
            String value,
            int position,
            boolean overview,
            @Nullable Integer attendanceFieldId,
            boolean isPublic) {
        return SqlSupport.insertReturning(
                """
                INSERT INTO event_field(event_id, name, field_type, config, value, position, overview, attendance_field_id, public)
                VALUES (:event_id, :name, :field_type, :config::JSONB, :value, :position, :overview, :attendance_field_id, :public)
                RETURNING %s;""",
                call().bind("event_id", eventId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("value", value)
                        .bind("position", position)
                        .bind("overview", overview)
                        .bind("attendance_field_id", attendanceFieldId)
                        .bind("public", isPublic),
                AppointmentField.map(),
                EVENT_FIELD_COLUMNS);
    }

    private static final String GROUP_FIELD_COLUMNS =
            "f.name, f.field_type, f.config, f.value, f.position, f.overview, NULL::INT AS attendance_field_id, f.\"public\"";

    /**
     * What an appointment shows on one of its dates: the fields its event group hands down, then its
     * own as they stand on that date, an own field replacing a group field of the same name.
     *
     * @param eventId the appointment
     * @param date    the date
     * @return the fields it shows
     */
    public List<AppointmentField> findShownOn(int eventId, LocalDate date) {
        var own = findByEventOn(eventId, date);
        return AppointmentField.shown(
                own,
                findInherited(List.of(eventId), false),
                own.stream().map(AppointmentField::name).toList());
    }

    /**
     * The fields of an event group as its own page shows them. They belong to no appointment, so they
     * carry event id 0.
     *
     * @param groupId the event group
     * @return its fields in order
     */
    public List<AppointmentField> findByGroup(int groupId) {
        return query("""
                SELECT f.id, 0 AS event_id, %s
                FROM event_group_field f
                WHERE f.group_id = :group_id
                ORDER BY f.position, f.id;""", GROUP_FIELD_COLUMNS)
                .single(call().bind("group_id", groupId))
                .map(AppointmentField.map())
                .all();
    }

    /**
     * The fields appointments take over from the event group they belong to, each under the
     * appointment it shows on. An inherited field carries the negative id of the group's field, so it
     * never stands for a field of the appointment itself.
     *
     * @param eventIds     the appointments
     * @param overviewOnly whether only the fields shown in lists are wanted
     * @return the inherited fields, by appointment and position
     */
    public List<AppointmentField> findInherited(List<Integer> eventIds, boolean overviewOnly) {
        if (eventIds.isEmpty()) return List.of();
        return query("""
                SELECT -f.id AS id, se.id AS event_id, %s
                FROM station_event se
                    JOIN event_group_set s ON s.id = se.event_group_set_id
                    JOIN event_group_field f ON f.group_id = s.group_id
                WHERE se.id = ANY(:event_ids)
                  AND (NOT :overview_only OR f.overview)
                ORDER BY se.id, f.position, f.id;""", GROUP_FIELD_COLUMNS)
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER)
                        .bind("overview_only", overviewOnly))
                .map(AppointmentField.map())
                .all();
    }

    /**
     * The names of the fields appointments carry themselves, which decide whether an appointment
     * replaces a field of its event group.
     *
     * @param eventIds the appointments
     * @return the names of each appointment's own fields
     */
    public Map<Integer, Set<String>> findOwnNames(List<Integer> eventIds) {
        var names = new HashMap<Integer, Set<String>>();
        if (eventIds.isEmpty()) return names;
        query("SELECT event_id, name FROM event_field WHERE event_id = ANY(:event_ids);")
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER))
                .map(row -> Map.entry(row.getInt("event_id"), row.getString("name")))
                .all()
                .forEach(entry -> names.computeIfAbsent(entry.getKey(), id -> new HashSet<>())
                        .add(entry.getValue()));
        return names;
    }

    /**
     * Replaces the fields of an event group. Nothing hangs off a group field's row, so they are
     * written anew.
     *
     * @param groupId the event group
     * @param fields  the fields in their new order
     */
    public void replaceGroupFields(int groupId, List<EventFieldDraft> fields) {
        query("DELETE FROM event_group_field WHERE group_id = :group_id;")
                .single(call().bind("group_id", groupId))
                .delete();
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            query("""
                    INSERT INTO event_group_field(group_id, name, field_type, config, value, position, overview, "public")
                    VALUES (:group_id, :name, :field_type, :config::JSONB, :value, :position, :overview, :public);""")
                    .single(call().bind("group_id", groupId)
                            .bind("name", field.name())
                            .bind("field_type", field.fieldType())
                            .bind("config", field.config().toJson())
                            .bind("value", field.value())
                            .bind("position", i)
                            .bind("overview", field.overview())
                            .bind("public", field.isPublic()))
                    .insert();
        }
    }

    public void deleteByEvent(int eventId) {
        query("DELETE FROM event_field WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
    }

    /**
     * Writes the questions an appointment asks, keeping the row of every question that was already
     * there.
     *
     * <p>Rewriting them wholesale was simpler and threw away the id, which is what the answers given
     * per date hang off: saving the appointment would have emptied every one of them. So a question
     * the editor already knows is written over its own row, one it does not know is added, and one it
     * no longer names is deleted with its answers.
     *
     * <p>An appointment that took over dates of another one started with copies of its questions, and
     * the editor saving it right after still holds the ids of the originals. Such an id stands for its
     * copy, so the answers the copy carries for the dates taken over stay where they are.
     */
    public void replaceFields(int eventId, List<EventFieldDraft> fields) {
        var copies = copiesOfSourceQuestions(eventId);
        var kept = new ArrayList<Integer>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            Integer named = field.id();
            Integer fieldId = named == null ? null : copies.getOrDefault(named, named);
            if (fieldId != null && update(fieldId, eventId, field, i)) {
                kept.add(fieldId);
                continue;
            }
            kept.add(create(
                            eventId,
                            field.name(),
                            field.fieldType(),
                            field.config(),
                            field.value(),
                            i,
                            field.overview(),
                            field.attendanceFieldId(),
                            field.isPublic())
                    .id());
        }
        deleteByEventExcept(eventId, kept);
    }

    /**
     * The questions of the appointment this one took over from, each with its copy here: the question
     * at the same place with the same name.
     */
    private Map<Integer, Integer> copiesOfSourceQuestions(int eventId) {
        var copies = new HashMap<Integer, Integer>();
        query("""
                SELECT original.id AS original_id, copy.id AS copy_id
                FROM station_event e
                         JOIN event_field original ON original.event_id = e.continues_event_id
                         JOIN event_field copy
                              ON copy.event_id = e.id
                                  AND copy.position = original.position
                                  AND copy.name = original.name
                WHERE e.id = :event_id;""")
                .single(call().bind("event_id", eventId))
                .map(row -> Map.entry(row.getInt("original_id"), row.getInt("copy_id")))
                .all()
                .forEach(entry -> copies.put(entry.getKey(), entry.getValue()));
        return copies;
    }

    private boolean update(int fieldId, int eventId, EventFieldDraft field, int position) {
        return query("""
                UPDATE event_field
                SET name = :name,
                    field_type = :field_type,
                    config = :config::JSONB,
                    value = :value,
                    position = :position,
                    overview = :overview,
                    attendance_field_id = :attendance_field_id,
                    public = :public
                WHERE id = :id AND event_id = :event_id;""")
                .single(call().bind("id", fieldId)
                        .bind("event_id", eventId)
                        .bind("name", field.name())
                        .bind("field_type", field.fieldType())
                        .bind("config", field.config().toJson())
                        .bind("value", field.value())
                        .bind("position", position)
                        .bind("overview", field.overview())
                        .bind("attendance_field_id", field.attendanceFieldId())
                        .bind("public", field.isPublic()))
                .update()
                .changed();
    }

    private void deleteByEventExcept(int eventId, List<Integer> keptIds) {
        query("""
                DELETE FROM event_field
                WHERE event_id = :event_id AND NOT (id = ANY(:kept));""")
                .single(call().bind("event_id", eventId).bind("kept", keptIds, PostgreSqlTypes.INTEGER))
                .delete();
    }
}
