/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record InventoryRequirement(int id, int inventoryId, int roleId, int groupId, int quantity, int position) {
    public static RowMapping<InventoryRequirement> map() {
        return row -> new InventoryRequirement(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getInt("role_id"),
                row.getInt("group_id"),
                row.getInt("quantity"),
                row.getInt("position"));
    }
}
