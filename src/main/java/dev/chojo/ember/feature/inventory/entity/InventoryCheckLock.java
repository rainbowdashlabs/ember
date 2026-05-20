/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a lock acquired on a member during an inventory check to prevent concurrent checks.
 *
 * @param id        the unique lock identifier
 * @param stationId the station this lock belongs to
 * @param memberId  the member being checked (locked)
 * @param lockedBy  the member who acquired the lock (the checker)
 * @param lockedAt  when the lock was acquired
 */
public record InventoryCheckLock(int id, int stationId, int memberId, int lockedBy, Instant lockedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryCheckLock> map() {
        return row -> new InventoryCheckLock(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getInt("locked_by"),
                row.get("locked_at", INSTANT_TIMESTAMP));
    }
}
