/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a single item result within an inventory check.
 *
 * @param id          the unique check item identifier
 * @param checkId     the parent inventory check this result belongs to
 * @param itemId      the checked inventory item, or {@code null} if referring to an inventory in general
 * @param inventoryId the inventory the item belongs to, or {@code null} if item-level
 * @param result      the outcome of the check for this item
 * @param note        an optional note about the check result
 */
public record InventoryCheckItem(
        int id, int checkId, Integer itemId, Integer inventoryId, CheckResult result, String note) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryCheckItem> map() {
        return row -> new InventoryCheckItem(
                row.getInt("id"),
                row.getInt("check_id"),
                row.getObject("item_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getEnum("result", CheckResult.class),
                row.getString("note"));
    }
}
