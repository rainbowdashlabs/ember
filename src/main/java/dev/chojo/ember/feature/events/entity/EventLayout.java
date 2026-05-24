/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record EventLayout(int id, int stationId, String name) {

    public static RowMapping<EventLayout> map() {
        return row -> new EventLayout(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
