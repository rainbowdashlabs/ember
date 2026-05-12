/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record InventoryCheckLock(int id, int stationId, int memberId, int lockedBy, Instant lockedAt) {
    public static RowMapping<InventoryCheckLock> map() {
        return row -> new InventoryCheckLock(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getInt("locked_by"),
                row.get("locked_at", INSTANT_TIMESTAMP));
    }
}
