/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * Represents a break period during which recurring events are suspended.
 *
 * @param id        the unique identifier of the break
 * @param stationId the station this break belongs to
 * @param name      the display name of the break (e.g. "Summer holidays")
 * @param startDate the first day of the break (inclusive)
 * @param endDate   the last day of the break (inclusive)
 */
public record EventBreak(int id, int stationId, String name, LocalDate startDate, LocalDate endDate) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<EventBreak> map() {
        return row -> new EventBreak(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getObject("start_date", LocalDate.class),
                row.getObject("end_date", LocalDate.class));
    }
}
