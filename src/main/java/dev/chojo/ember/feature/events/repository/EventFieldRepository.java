/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.events.entity.EventField;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Repository for managing custom fields attached to station events.
 */
@Singleton
public class EventFieldRepository {

    /**
     * Retrieves all fields for a given event, ordered by position.
     *
     * @param eventId the event ID
     * @return the list of event fields
     */
    public List<EventField> findByEvent(int eventId) {
        return Query.query(
                        "SELECT id, event_id, name, value, position FROM event_field WHERE event_id = :event_id ORDER BY position;")
                .single(Call.of().bind("event_id", eventId))
                .map(EventField.map())
                .all();
    }

    /**
     * Retrieves all distinct field names used across events of a station.
     *
     * @param stationId the station ID
     * @return the sorted list of unique field names
     */
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

    /**
     * Creates a new event field.
     *
     * @param eventId  the event to attach the field to
     * @param name     the field name
     * @param value    the field value
     * @param position the display order position
     * @return the created event field
     */
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

    /**
     * Updates an existing event field.
     *
     * @param id       the field ID
     * @param name     the new field name
     * @param value    the new field value
     * @param position the new display order position
     * @return true if a row was updated
     */
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

    /**
     * Deletes an event field by ID.
     *
     * @param id the field ID
     * @return true if a row was deleted
     */
    public boolean delete(int id) {
        return Query.query("DELETE FROM event_field WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .delete()
                .changed();
    }

    /**
     * Deletes all fields belonging to an event.
     *
     * @param eventId the event ID
     */
    public void deleteByEvent(int eventId) {
        Query.query("DELETE FROM event_field WHERE event_id = :event_id;")
                .single(Call.of().bind("event_id", eventId))
                .delete();
    }

    /**
     * Replaces all fields for an event by deleting existing ones and creating new entries with sequential positions.
     *
     * @param eventId the event ID
     * @param fields  the new field entries to create
     */
    public void replaceFields(int eventId, List<FieldEntry> fields) {
        deleteByEvent(eventId);
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            create(eventId, f.name(), f.value(), i);
        }
    }

    /**
     * A name-value pair used when replacing event fields.
     *
     * @param name  the field name
     * @param value the field value
     */
    public record FieldEntry(String name, String value) {}
}
