/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One question an event template asks.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty. Kept apart from the answer the appointment ends up
 *                     with, so changing the template leaves appointments already written alone
 */
public record EventTemplateField(
        int id,
        int templateId,
        String name,
        EventFieldType fieldType,
        EventFieldConfig config,
        int position,
        boolean overview,
        boolean isPublic,
        Integer attendanceFieldId,
        String defaultValue) {

    public static RowMapping<EventTemplateField> map() {
        return row -> new EventTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                row.getEnum("field_type", EventFieldType.class),
                EventFieldConfig.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getBoolean("public"),
                row.getObject("attendance_field_id", Integer.class),
                row.getString("default_value"));
    }
}
