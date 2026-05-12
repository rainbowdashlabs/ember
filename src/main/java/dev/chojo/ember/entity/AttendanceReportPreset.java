/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AttendanceReportPreset(
        int id, int stationId, String name, String roleName, Integer groupId, String period, String rounding) {
    public static RowMapping<AttendanceReportPreset> map() {
        return row -> new AttendanceReportPreset(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("role_name"),
                row.getObject("group_id", Integer.class),
                row.getString("period"),
                row.getString("rounding"));
    }
}
