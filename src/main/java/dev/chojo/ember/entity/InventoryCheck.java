/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record InventoryCheck(int id, int stationId, int memberId, int checkedBy, Instant checkedAt) {
    public static RowMapping<InventoryCheck> map() {
        return row -> new InventoryCheck(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getInt("checked_by"),
                row.get("checked_at", INSTANT_TIMESTAMP));
    }
}
