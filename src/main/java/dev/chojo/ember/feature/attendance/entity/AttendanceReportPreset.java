/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A saved preset for attendance report generation, storing filter and formatting options.
 *
 * @param id        unique preset identifier
 * @param stationId the station this preset belongs to
 * @param name      display name of the preset
 * @param roleName  optional role filter for the report
 * @param groupId   optional group filter for the report
 * @param period    time period granularity (e.g. "month", "year")
 * @param rounding  hour rounding mode (e.g. "exact", "ceil", "round")
 */
public record AttendanceReportPreset(
        int id, int stationId, String name, String roleName, Integer groupId, String period, String rounding) {
    /** Creates a row mapping for database result set conversion. */
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
