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
 * Repository for the {@code event_field_default} and {@code event_template_field_default} tables:
 * the prefill rules of an event, or of a template for the events made from it, that decide which value
 * an attendance template field starts with.
 */
@Singleton
public class EventFieldDefaultRepository {

    private static String table(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_field_default";
            case TEMPLATE -> "event_template_field_default";
        };
    }

    private static String ownerColumn(AppointmentOwner owner) {
        return switch (owner) {
            case EVENT -> "event_id";
            case TEMPLATE -> "template_id";
        };
    }

    /**
     * The starting values an event or a template holds for the fields of its attendance sheet. A
     * template's carries the template's id where an event's carries the event's.
     *
     * @param owner   whose values these are
     * @param ownerId the event or template
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
     * Replaces all field defaults of an event or a template by deleting existing ones and inserting
     * the given defaults.
     *
     * @param owner    whose values these are
     * @param ownerId  the event or template
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
