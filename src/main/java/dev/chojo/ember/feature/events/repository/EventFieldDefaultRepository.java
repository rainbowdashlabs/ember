/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code event_field_default} table: the per-event prefill rules that decide
 * which value an attendance template field starts with.
 */
@Singleton
public class EventFieldDefaultRepository {

    /**
     * Retrieves all field default configurations for an event.
     *
     * @param eventId the event ID
     * @return the list of field defaults
     */
    public List<EventFieldDefault> findByEvent(int eventId) {
        return query("""
                SELECT
                    event_id,
                    field_id,
                    source,
                    value
                FROM
                    event_field_default
                WHERE event_id = :event_id;""")
                .single(call().bind("event_id", eventId))
                .map(EventFieldDefault.map())
                .all();
    }

    /**
     * Replaces all field defaults for an event by deleting existing ones and inserting the given defaults.
     *
     * @param eventId  the event ID
     * @param defaults the new field default configurations
     */
    public void replaceForEvent(int eventId, List<EventFieldDefault> defaults) {
        query("DELETE FROM event_field_default WHERE event_id = :event_id;")
                .single(call().bind("event_id", eventId))
                .delete();
        for (var def : defaults) {
            query("""
                    INSERT INTO event_field_default(event_id, field_id, source, value)
                    VALUES (:event_id, :field_id, :source, :value);""")
                    .single(call().bind("event_id", eventId)
                            .bind("field_id", def.fieldId())
                            .bind("source", def.source())
                            .bind("value", def.value()))
                    .insert();
        }
    }
}
