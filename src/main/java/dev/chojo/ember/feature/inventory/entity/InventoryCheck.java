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
 * Represents a completed inventory check for a member.
 *
 * @param id        the unique check identifier
 * @param stationId the station where the check was performed
 * @param memberId  the member whose inventory was checked
 * @param checkedBy the member who performed the check
 * @param checkedAt when the check was completed
 */
public record InventoryCheck(int id, int stationId, int memberId, int checkedBy, Instant checkedAt) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<InventoryCheck> map() {
        return row -> new InventoryCheck(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getInt("checked_by"),
                row.get("checked_at", INSTANT_TIMESTAMP));
    }
}
