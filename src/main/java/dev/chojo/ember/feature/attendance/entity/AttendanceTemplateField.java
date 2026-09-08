/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.question.Question;

/**
 * A field definition within an attendance template, describing what data to collect per session.
 *
 * @param id         unique field identifier
 * @param templateId the template this field belongs to
 * @param name       display name of the field
 * @param fieldType  the type of field (e.g. text, member, attendance)
 * @param config     JSONB configuration string parsed via {@link AttendanceFieldConfig}
 * @param position   ordering position within the template
 */
public record AttendanceTemplateField(
        int id,
        int templateId,
        String name,
        AttendanceFieldType fieldType,
        AttendanceFieldConfig config,
        int position) {
    /**
     * This field as everything that checks a question reads it.
     *
     * <p>The row stays what it is; what it means is said once, here, so the same rules measure an
     * answer written on a sheet as measure one given to an appointment.
     */
    public Question question() {
        return config.settings().asQuestion(name, fieldType.kind());
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceTemplateField> map() {
        return row -> new AttendanceTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                row.getEnum("field_type", AttendanceFieldType.class),
                AttendanceFieldConfig.parse(row.getString("config")),
                row.getInt("position"));
    }
}
