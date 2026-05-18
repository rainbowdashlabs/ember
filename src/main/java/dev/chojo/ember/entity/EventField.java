/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventField(int id, int eventId, String name, String value, int position) {
    public static RowMapping<EventField> map() {
        return row -> new EventField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getString("value"),
                row.getInt("position"));
    }
}
