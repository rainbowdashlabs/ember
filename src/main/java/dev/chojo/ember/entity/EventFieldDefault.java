/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventFieldDefault(int eventId, int fieldId, String source, String value) {
    public static RowMapping<EventFieldDefault> map() {
        return row -> new EventFieldDefault(
                row.getInt("event_id"), row.getInt("field_id"), row.getString("source"), row.getString("value"));
    }
}
