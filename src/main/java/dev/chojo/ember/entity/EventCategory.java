/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventCategory(int id, int stationId, String name, int position) {
    public static RowMapping<EventCategory> map() {
        return row -> new EventCategory(
                row.getInt("id"), row.getInt("station_id"), row.getString("name"), row.getInt("position"));
    }
}
