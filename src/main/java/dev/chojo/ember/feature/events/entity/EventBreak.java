/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

public record EventBreak(int id, int stationId, String name, LocalDate startDate, LocalDate endDate) {
    public static RowMapping<EventBreak> map() {
        return row -> new EventBreak(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class));
    }
}
