/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

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
        int id, int templateId, String name, String fieldType, String config, int position) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceTemplateField> map() {
        return row -> new AttendanceTemplateField(
                row.getInt("id"),
                row.getInt("template_id"),
                row.getString("name"),
                row.getString("field_type"),
                row.getString("config"),
                row.getInt("position"));
    }
}
