/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record Inventory(int id, int stationId, String name, InventoryType inventoryType, boolean hasSizes) {
    public static RowMapping<Inventory> map() {
        return row -> new Inventory(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getEnum("inventory_type", InventoryType.class),
                row.getBoolean("has_sizes"));
    }
}
