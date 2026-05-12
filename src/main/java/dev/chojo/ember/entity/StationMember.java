/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record StationMember(int id, int stationId, int accountId) {
    public static RowMapping<StationMember> map() {
        return row -> new StationMember(row.getInt("id"), row.getInt("station_id"), row.getInt("account_id"));
    }
}
