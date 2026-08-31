/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Summary of an inventory with item and lost counts.
 */
public record InventorySummary(
        int id,
        int stationId,
        String name,
        InventoryType inventoryType,
        boolean hasSizes,
        boolean homogeneous,
        boolean borrowed,
        int itemCount,
        int lostCount,
        int procurementCount,
        int lentOutCount) {
    public static RowMapping<InventorySummary> map() {
        return row -> new InventorySummary(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getEnum("inventory_type", InventoryType.class),
                row.getBoolean("has_sizes"),
                row.getBoolean("homogeneous"),
                row.getBoolean("borrowed"),
                row.getInt("item_count"),
                row.getInt("lost_count"),
                row.getInt("procurement_count"),
                row.getInt("lent_out_count"));
    }
}
