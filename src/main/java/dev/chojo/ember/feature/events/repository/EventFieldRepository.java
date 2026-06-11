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
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class EventFieldRepository {

    private static final String ALL_COLUMNS =
            "id, event_id, name, field_type, config, value, position, overview, attendance_field_id, \"public\"";

    public List<EventField> findByEvent(int eventId) {
        return query("SELECT " + ALL_COLUMNS + " FROM event_field WHERE event_id = :event_id ORDER BY position;")
                .single(call().bind("event_id", eventId))
                .map(EventField.map())
                .all();
    }

    public List<EventField> findOverviewFieldsByEvents(List<Integer> eventIds) {
        if (eventIds.isEmpty()) return List.of();
        return query(
                        "SELECT " + ALL_COLUMNS
                                + " FROM event_field WHERE event_id = ANY(:event_ids) AND overview ORDER BY event_id, position;")
                .single(call().bind("event_ids", eventIds, PostgreSqlTypes.INTEGER))
                .map(EventField.map())
                .all();
    }

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
        return query(
                        "INSERT INTO event_field(event_id, name, field_type, config, value, position, overview, attendance_field_id, \"public\")"
                                + " VALUES (:event_id, :name, :field_type, :config::jsonb, :value, :position, :overview, :attendance_field_id, :public)"
                                + " RETURNING " + ALL_COLUMNS + ";")
                .single(call().bind("event_id", eventId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config.toJson())
                        .bind("value", value)
                        .bind("position", position)
                        .bind("overview", overview)
                        .bind("attendance_field_id", attendanceFieldId)
                        .bind("public", isPublic))
                .map(EventField.map())
                .first()
                .orElseThrow();
    }

    public void deleteByEvent(int eventId) {
        query("DELETE FROM event_field WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
    }

    public void replaceFields(int eventId, List<FieldEntry> fields) {
        deleteByEvent(eventId);
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            create(
                    eventId,
                    f.name(),
                    f.fieldType() != null ? f.fieldType() : EventFieldType.STRING,
                    f.config() != null ? f.config() : EventFieldConfig.parse("{}"),
                    f.value() != null ? f.value() : "",
                    i,
                    f.overview(),
                    f.attendanceFieldId(),
                    f.isPublic());
        }
    }

    public record FieldEntry(
            String name,
            EventFieldType fieldType,
            EventFieldConfig config,
            String value,
            boolean overview,
            Integer attendanceFieldId,
            boolean isPublic) {}
}
