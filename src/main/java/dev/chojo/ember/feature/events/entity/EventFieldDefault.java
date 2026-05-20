/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a default value configuration for an event field, allowing fields to be auto-populated
 * from event properties or static values.
 *
 * @param eventId the event this default belongs to
 * @param fieldId the attendance field to populate
 * @param source  the source type (e.g. "VALUE", "EVENT_NAME", "EVENT_DESCRIPTION", "EVENT_START_TIME", "EVENT_END_TIME")
 * @param value   the static value when source is "VALUE", otherwise unused
 */
public record EventFieldDefault(int eventId, int fieldId, String source, String value) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<EventFieldDefault> map() {
        return row -> new EventFieldDefault(
                row.getInt("event_id"), row.getInt("field_id"), row.getString("source"), row.getString("value"));
    }
}
