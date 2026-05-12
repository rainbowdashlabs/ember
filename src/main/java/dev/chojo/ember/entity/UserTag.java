/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record UserTag(int id, int stationId, String name) {
    public static RowMapping<UserTag> map() {
        return row -> new UserTag(row.getInt("id"), row.getInt("station_id"), row.getString("name"));
    }
}
