/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventFieldValue(int eventId, int fieldId, String value) {
    public static RowMapping<EventFieldValue> map() {
        return row -> new EventFieldValue(row.getInt("event_id"), row.getInt("field_id"), row.getString("value"));
    }
}
