/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public Optional<EventField> findById(int fieldId) {
        return SqlSupport.findById("event_field", EVENT_FIELD_COLUMNS, fieldId, EventField.map());
    }

    /** One question as it stands on one date of the appointment. */
    public Optional<EventField> findByIdOn(int fieldId, LocalDate date) {
        return query("""
                SELECT %s
                %s
                WHERE ef.id = :id;""", EVENT_FIELD_COLUMNS_ON_DATE, ON_DATE_JOIN)
                .single(call().bind("id", fieldId).bind("event_date", date))
                .map(EventField.map())
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

    public List<EventField> findByEvent(int eventId) {
        return query("""
                SELECT %s
                FROM event_field
                WHERE event_id = :event_id
                ORDER BY position;""", EVENT_FIELD_COLUMNS)
                .single(call().bind("event_id", eventId))
                .map(EventField.map())
                .all();
    }

    /** Every question of an appointment as it stands on one of its dates. */
    public List<EventField> findByEventOn(int eventId, LocalDate date) {
        return query("""
                SELECT %s
                %s
                WHERE ef.event_id = :event_id
                ORDER BY ef.position;""", EVENT_FIELD_COLUMNS_ON_DATE, ON_DATE_JOIN)
                .single(call().bind("event_id", eventId).bind("event_date", date))
                .map(EventField.map())
                .all();
    }

    public List<EventField> findOverviewFieldsByEvents(List<Integer> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM event_field
                WHERE event_id = ANY(:event_ids) AND overview
                ORDER BY event_id, position;""", EVENT_FIELD_COLUMNS)
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER))
                .map(EventField.map())
                .all();
    }

    /**
     * The questions marked for the overview, each as it stands on one date of its own appointment.
     *
     * <p>Every appointment on a list of them is drawn on a different day, so one date for the whole
     * query would answer most of them wrongly. The dates arrive as two lists read in step, which is
     * how a set of pairs travels into a single statement.
     */
    public List<EventField> findOverviewFieldsByEventsOn(List<Integer> eventIds, List<LocalDate> dates) {
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
                .map(EventField.map())
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

    /** The appointments that ask at least one question naming members. */
    public List<Integer> findEventIdsWithMemberFields() {
        var memberTypes = Arrays.stream(EventFieldType.values())
                .filter(EventFieldType::isMemberField)
                .map(Enum::name)
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
                SELECT DISTINCT ef.name
                FROM event_field ef
                JOIN station_event se ON se.id = ef.event_id
                WHERE se.station_id = :station_id
                ORDER BY ef.name;""")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString("name"))
                .all();
    }

    public EventField create(
            int eventId,
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            String value,
            int position,
            boolean overview,
            Integer attendanceFieldId,
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
                EventField.map(),
                EVENT_FIELD_COLUMNS);
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
     */
    public void replaceFields(int eventId, List<FieldEntry> fields) {
        var kept = new ArrayList<Integer>(fields.size());
        for (int i = 0; i < fields.size(); i++) {
            var field = fields.get(i);
            var type = field.fieldType() != null ? field.fieldType() : EventFieldType.STRING;
            var config = field.config() != null ? field.config() : EventFieldConfig.empty();
            String value = field.value() != null ? field.value() : "";
            if (field.id() != null && update(field.id(), eventId, field, type, config, value, i)) {
                kept.add(field.id());
                continue;
            }
            kept.add(create(
                            eventId,
                            field.name(),
                            type,
                            config,
                            value,
                            i,
                            field.overview(),
                            field.attendanceFieldId(),
                            field.isPublic())
                    .id());
        }
        deleteByEventExcept(eventId, kept);
    }

    private boolean update(
            int fieldId,
            int eventId,
            FieldEntry field,
            EventFieldType type,
            EventFieldConfig config,
            String value,
            int position) {
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
                        .bind("field_type", type)
                        .bind("config", config.toJson())
                        .bind("value", value)
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

    public record FieldEntry(
            Integer id,
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            String value,
            boolean overview,
            Integer attendanceFieldId,
            boolean isPublic) {

        /**
         * A question the caller does not name a row for, which is one being asked for the first
         * time. Everything that builds questions from scratch, a template or an import says it this
         * way; only the editor, which is writing over questions that already exist, names ids.
         */
        public FieldEntry(
                String name,
                EventFieldType fieldType,
                EventFieldConfig config,
                String value,
                boolean overview,
                Integer attendanceFieldId,
                boolean isPublic) {
            this(null, name, fieldType, config, value, overview, attendanceFieldId, isPublic);
        }
    }
}
