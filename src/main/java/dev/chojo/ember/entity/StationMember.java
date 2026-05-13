/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record StationMember(int id, int stationId, Integer accountId, boolean former, String displayName) {
    public static RowMapping<StationMember> map() {
        return row -> new StationMember(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("account_id", Integer.class),
                row.getBoolean("former"),
                row.getString("display_name"));
    }
}
