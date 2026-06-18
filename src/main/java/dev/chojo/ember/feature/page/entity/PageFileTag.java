/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record PageFileTag(int id, int stationId, String name, String color) {

    public static RowMapping<PageFileTag> map() {
        return row -> new PageFileTag(
                row.getInt("id"), row.getInt("station_id"), row.getString("name"), row.getString("color"));
    }
}
