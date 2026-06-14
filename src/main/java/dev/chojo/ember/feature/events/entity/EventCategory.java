/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventCategory(
        int id, int stationId, String name, int position, Integer maxShownEvents, boolean isPublic, String color) {

    public static RowMapping<EventCategory> map() {
        return row -> new EventCategory(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getInt("position"),
                row.getObject("max_shown_events", Integer.class),
                row.getBoolean("public"),
                row.getString("color"));
    }
}
