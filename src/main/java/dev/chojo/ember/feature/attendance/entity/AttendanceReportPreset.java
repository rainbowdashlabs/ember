/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.StationUserType;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

/**
 * A saved preset for attendance report generation: the filter as it stood on screen, apart from the
 * point in time, which is always the current one when the preset is applied.
 *
 * @param id        unique preset identifier
 * @param stationId the station this preset belongs to
 * @param name      display name of the preset
 * @param userTypes every user type the filter selects
 * @param groupIds  every group the filter selects; a group deleted since keeps its identifier here
 * @param period    time period granularity (e.g. "month", "year")
 * @param rounding  hour rounding mode (e.g. "exact", "ceil", "round")
 */
public record AttendanceReportPreset(
        int id,
        int stationId,
        String name,
        List<StationUserType> userTypes,
        List<Integer> groupIds,
        String period,
        String rounding) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AttendanceReportPreset> map() {
        return row -> new AttendanceReportPreset(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                elements(row.getArray("user_types"), String[].class).stream()
                        .map(StationUserType::valueOf)
                        .toList(),
                elements(row.getArray("group_ids"), Integer[].class),
                row.getString("period"),
                row.getString("rounding"));
    }

    private static <T> List<T> elements(Array array, Class<T[]> type) throws SQLException {
        return List.of(type.cast(array.getArray()));
    }
}
