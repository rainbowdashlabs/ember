/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventLayoutField(
        int id,
        int layoutId,
        String name,
        String fieldType,
        String config,
        int position,
        boolean overview,
        Integer attendanceFieldId) {

    public static RowMapping<EventLayoutField> map() {
        return row -> new EventLayoutField(
                row.getInt("id"),
                row.getInt("layout_id"),
                row.getString("name"),
                row.getString("field_type"),
                row.getString("config"),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getObject("attendance_field_id", Integer.class));
    }
}
