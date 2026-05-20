/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A field value recorded for a specific attendance session, linking a template field to its JSONB value.
 *
 * @param sessionId the session this field value belongs to
 * @param fieldId   the template field this value corresponds to
 * @param value     the JSONB value stored for this field
 */
public record AttendanceSessionField(int sessionId, int fieldId, String value) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceSessionField> map() {
        return row ->
                new AttendanceSessionField(row.getInt("session_id"), row.getInt("field_id"), row.getString("value"));
    }
}
