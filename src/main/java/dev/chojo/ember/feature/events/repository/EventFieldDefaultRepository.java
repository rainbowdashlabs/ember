/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.events.entity.AppointmentOwner;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the {@code event_field_default}, {@code event_template_field_default} and
 * {@code event_group_field_default} tables: the prefill rules of an event, of a template for the events
 * made from it, or of an event group for its parts, that decide which value an attendance template
 * field starts with.
 */
@Singleton
public class EventFieldDefaultRepository {

    private static String table(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_field_default";
            case TEMPLATE -> "event_template_field_default";
            case EVENT_GROUP -> "event_group_field_default";
        };
    }

    private static String ownerColumn(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_id";
            case TEMPLATE -> "template_id";
            case EVENT_GROUP -> "event_group_id";
        };
    }

    /**
     * The starting values an event, a template or an event group holds for the fields of its attendance
     * sheet. Each carries its owner's id: a template's the template's, an event group's the event group's.
     *
     * @param owner   whose values these are
     * @param ownerId the event, template or event group
     * @return the field defaults
     */
    public List<EventFieldDefault> findByOwner(AppointmentOwner owner, int ownerId) {
        return query("""
                SELECT
                    %s AS event_id,
                    field_id,
                    source,
                    value
                FROM
                    %s
                WHERE %1$s = :owner_id;""", ownerColumn(owner), table(owner))
                .single(call().bind("owner_id", ownerId))
                .map(EventFieldDefault.map())
                .all();
    }

    /**
     * The starting values that take effect for an appointment's attendance sheet. A part of an event
     * group without an attendance template of its own takes the event group's template and with it the
     * event group's values; every other appointment its own. Each carries the appointment's id.
     *
     * @param eventId the appointment
     * @return the field defaults
     */
    public List<EventFieldDefault> findEffective(int eventId) {
        return query("""
                SELECT d.event_id, d.field_id, d.source, d.value
                FROM event_field_default d
                         JOIN station_event e ON e.id = d.event_id
                WHERE d.event_id = :event_id
                  AND (e.template_id IS NOT NULL OR e.event_group_set_id IS NULL)
                UNION ALL
                SELECT e.id AS event_id, d.field_id, d.source, d.value
                FROM event_group_field_default d
                         JOIN event_group_set gs ON gs.event_group_id = d.event_group_id
                         JOIN station_event e ON e.event_group_set_id = gs.id
                WHERE e.id = :event_id
                  AND e.template_id IS NULL;""")
                .single(call().bind("event_id", eventId))
                .map(EventFieldDefault.map())
                .all();
    }

    /**
     * Replaces all field defaults of an event, a template or an event group by deleting existing ones
     * and inserting the given defaults.
     *
     * @param owner    whose values these are
     * @param ownerId  the event, template or event group
     * @param defaults the new field default configurations
     */
    public void replace(AppointmentOwner owner, int ownerId, List<EventFieldDefault> defaults) {
        query("DELETE FROM %s WHERE %s = :owner_id;", table(owner), ownerColumn(owner))
                .single(call().bind("owner_id", ownerId))
                .delete();
        for (var def : defaults) {
            query("""
                    INSERT INTO %s(%s, field_id, source, value)
                    VALUES (:owner_id, :field_id, :source, :value);""", table(owner), ownerColumn(owner))
                    .single(call().bind("owner_id", ownerId)
                            .bind("field_id", def.fieldId())
                            .bind("source", def.source())
                            .bind("value", def.value()))
                    .insert();
        }
    }
}
