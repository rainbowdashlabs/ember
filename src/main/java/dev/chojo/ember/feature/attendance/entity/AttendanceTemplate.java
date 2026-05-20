/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An attendance template defining the structure and fields for attendance sessions.
 *
 * @param id        unique template identifier
 * @param stationId the station this template belongs to
 * @param name      display name of the template
 */
public record AttendanceTemplate(int id, int stationId, String name) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<AttendanceTemplate> map() {
        return row -> new AttendanceTemplate(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
