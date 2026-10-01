/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.FieldType;
import org.jspecify.annotations.Nullable;

/**
 * One question an appointment template hands the organiser of every appointment made from it.
 *
 * <p>What the server works with and the template editor reads.
 *
 * @param defaultValue what an appointment made from this template starts the question off with, or
 *                     null where it starts empty. Kept apart from the answer the appointment ends up
 *                     with, so changing the template leaves appointments already written alone
 */
public record AppointmentTemplateField(
        int id,
        int templateId,
        String name,
        FieldType fieldType,
        EventQuestionSettings config,
        int position,
        boolean overview,
        boolean isPublic,
        @Nullable Integer attendanceFieldId,
        @Nullable String defaultValue) {

    public static RowMapping<AppointmentTemplateField> map() {
        return row -> new AppointmentTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                FieldType.valueOf(row.getString("field_type")),
                EventQuestionSettings.parse(row.getString("config")),
                row.getInt("position"),
                row.getBoolean("overview"),
                row.getBoolean("public"),
                row.getObject("attendance_field_id", Integer.class),
                row.getString("default_value"));
    }
}
