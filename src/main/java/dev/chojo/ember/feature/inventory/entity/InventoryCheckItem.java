/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record InventoryCheckItem(
        int id, int checkId, Integer itemId, Integer inventoryId, CheckResult result, String note) {
    public static RowMapping<InventoryCheckItem> map() {
        return row -> new InventoryCheckItem(
                row.getInt("id"),
                row.getInt("check_id"),
                row.getObject("item_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                CheckResult.valueOf(row.getString("result")),
                row.getString("note"));
    }
}
