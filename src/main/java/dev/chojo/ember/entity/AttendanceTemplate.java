/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record AttendanceTemplate(int id, int stationId, String name) {
    public static RowMapping<AttendanceTemplate> map() {
        return row -> new AttendanceTemplate(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
