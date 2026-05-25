/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventField(
        int id,
        int eventId,
        String name,
        String fieldType,
        String config,
        String value,
        int position,
        boolean overview,
        Integer attendanceFieldId,
        boolean isPublic) {

    public static RowMapping<EventField> map() {
        return row -> new EventField(
                row.getInt("id"),
                row.getInt("event_id"),
                row.getString("name"),
                row.getString("field_type"),
                row.getString("config"),
                row.getString("value"),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getObject("attendance_field_id", Integer.class),
                row.getBoolean("public"));
    }
}
