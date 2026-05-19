/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.EventField;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class EventFieldRepository {

    public List<EventField> findByEvent(int eventId) {
        return Query.query(
                        "SELECT id, event_id, name, value, position FROM event_field WHERE event_id = :event_id ORDER BY position;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventField.map())
                .all();
    }

    public List<String> findDistinctFieldNames(int stationId) {
        return Query.query("""
                        SELECT DISTINCT ef.name
                        FROM event_field ef
                        JOIN station_event se ON se.id = ef.event_id
                        WHERE se.station_id = :station_id
                        ORDER BY ef.name;""")
                .single(Call.of().bind("station_id", stationId))
                .map(row -> row.getString("name"))
                .all();
    }

    public EventField create(int eventId, String name, String value, int position) {
        return Query.query("""
                        INSERT INTO event_field(event_id, name, value, position)
                        VALUES (:event_id, :name, :value, :position)
                        RETURNING id, event_id, name, value, position;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("name", name)
                        .bind("value", value)
                        .bind("position", position))
                .map(EventField.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String value, int position) {
        return Query.query("""
                        UPDATE event_field
                        SET name = :name, value = :value, position = :position
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("value", value)
                        .bind("position", position))
                .update()
                .changed();
    }

    public boolean delete(int id) {
        return Query.query("DELETE FROM event_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    public void deleteByEvent(int eventId) {
        Query.query("DELETE FROM event_field WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
    }

    public void replaceFields(int eventId, List<FieldEntry> fields) {
        deleteByEvent(eventId);
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            create(eventId, f.name(), f.value(), i);
        }
    }

    public record FieldEntry(String name, String value) {}
}
