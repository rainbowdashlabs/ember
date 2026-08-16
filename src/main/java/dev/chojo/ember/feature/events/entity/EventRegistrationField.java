/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A question an event asks of everyone registering for it. Unlike {@link EventField}, which
 * describes the event and holds one value, this holds one answer per registration.
 */
public record EventRegistrationField(
        int id,
        int eventId,
        String name,
        EventFieldType fieldType,
        EventRegistrationFieldConfig config,
        int position,
        boolean overview) {

    public static RowMapping<EventRegistrationField> map() {
        return row -> new EventRegistrationField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getEnum("field_type", EventFieldType.class),
                EventRegistrationFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"));
    }
}
