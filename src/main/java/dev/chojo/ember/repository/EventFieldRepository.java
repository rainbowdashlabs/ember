/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.entity.EventField;
import dev.chojo.ember.entity.EventFieldValue;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class EventFieldRepository {

    // -- Field Definitions --

    public List<EventField> findByStation(int stationId) {
        return Query.query(
                        "SELECT id, station_id, name, field_type, config, position FROM event_field WHERE station_id = :station_id ORDER BY position;")
                .single(Call.of().bind("station_id", stationId))
                .map(EventField.map())
                .all();
    }

    public Optional<EventField> findById(int id) {
        return Query.query("SELECT id, station_id, name, field_type, config, position FROM event_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(EventField.map())
                .first();
    }

    public EventField create(int stationId, String name, String fieldType, String config, int position) {
        return Query.query("""
                        INSERT INTO event_field(station_id, name, field_type, config, position)
                        VALUES (:station_id, :name, :field_type, :config::JSONB, :position)
                        RETURNING id, station_id, name, field_type, config, position;""")
                .single(Call.of()
                        .bind("station_id", stationId)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config)
                        .bind("position", position))
                .map(EventField.map())
                .first()
                .orElseThrow();
    }

    public boolean update(int id, String name, String fieldType, String config, int position) {
        return Query.query("""
                        UPDATE event_field
                        SET name       = :name,
                            field_type = :field_type,
                            config     = :config::JSONB,
                            position   = :position
                        WHERE id = :id;""")
                .single(Call.of()
                        .bind("id", id)
                        .bind("name", name)
                        .bind("field_type", fieldType)
                        .bind("config", config)
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

    // -- Field Values --

    public List<EventFieldValue> findValues(int eventId) {
        return Query.query("SELECT event_id, field_id, value FROM event_field_value WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventFieldValue.map())
                .all();
    }

    public void setValue(int eventId, int fieldId, String value) {
        Query.query("""
                        INSERT INTO event_field_value(event_id, field_id, value)
                        VALUES (:event_id, :field_id, :value::JSONB)
                        ON CONFLICT (event_id, field_id) DO UPDATE SET value = :value::JSONB;""")
                .single(Call.of()
                        .bind("event_id", eventId)
                        .bind("field_id", fieldId)
                        .bind("value", value))
                .insert();
    }

    public boolean deleteValue(int eventId, int fieldId) {
        return Query.query("DELETE FROM event_field_value WHERE event_id = :event_id AND field_id = :field_id;")
                .single(Call.of().bind("event_id", eventId).bind("field_id", fieldId))
                .delete()
                .changed();
    }
}
