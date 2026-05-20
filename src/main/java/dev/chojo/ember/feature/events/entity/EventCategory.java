/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a category used to group events within a station.
 *
 * @param id        the unique identifier of the category
 * @param stationId the station this category belongs to
 * @param name      the display name of the category
 * @param position  the display order position
 */
public record EventCategory(int id, int stationId, String name, int position) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<EventCategory> map() {
        return row -> new EventCategory(
                row.getInt("id"), row.getInt("station_id"), row.getString("name"), row.getInt("position"));
    }
}
