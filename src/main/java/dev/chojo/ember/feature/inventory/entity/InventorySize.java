/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record InventorySize(int id, int inventoryId, String label, int position, String note) {
    public static RowMapping<InventorySize> map() {
        return row -> new InventorySize(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("label"),
                row.getInt("position"),
                row.getString("note"));
    }
}
