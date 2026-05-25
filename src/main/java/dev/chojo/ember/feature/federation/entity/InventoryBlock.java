/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * Blocks lending of inventory items during a specific date range.
 */
public record InventoryBlock(
        int id,
        int stationId,
        Integer inventoryId,
        Integer itemId,
        LocalDate blockFrom,
        LocalDate blockTo,
        String reason) {

    public static RowMapping<InventoryBlock> map() {
        return row -> new InventoryBlock(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getObject("block_from", LocalDate.class),
                row.getObject("block_to", LocalDate.class),
                row.getString("reason"));
    }
}
