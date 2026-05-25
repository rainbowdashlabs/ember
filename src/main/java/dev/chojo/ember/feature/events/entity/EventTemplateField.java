/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventTemplateField(
        int id,
        int templateId,
        String name,
        String fieldType,
        String config,
        int position,
        boolean overview,
        boolean isPublic,
        Integer attendanceFieldId) {

    public static RowMapping<EventTemplateField> map() {
        return row -> new EventTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                row.getString("field_type"),
                row.getString("config"),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getBoolean("public"),
                row.getObject("attendance_field_id", Integer.class));
    }
}
