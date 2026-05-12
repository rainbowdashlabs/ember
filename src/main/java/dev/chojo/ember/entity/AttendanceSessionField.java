/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AttendanceSessionField(int sessionId, int fieldId, String value) {
    public static RowMapping<AttendanceSessionField> map() {
        return row ->
                new AttendanceSessionField(row.getInt("session_id"), row.getInt("field_id"), row.getString("value"));
    }
}
