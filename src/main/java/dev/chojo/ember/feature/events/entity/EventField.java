/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a custom field attached to a station event.
 *
 * @param id       the unique identifier of the field
 * @param eventId  the event this field belongs to
 * @param name     the field name (label)
 * @param value    the field value
 * @param position the display order position
 */
public record EventField(int id, int eventId, String name, String value, int position) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<EventField> map() {
        return row -> new EventField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getString("value"),
                row.getInt("position"));
    }
}
