/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AttendanceTemplateField(
        int id, int templateId, String name, String fieldType, String config, int position) {
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
