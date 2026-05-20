/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a size variant within an inventory.
 *
 * @param id          the unique size identifier
 * @param inventoryId the inventory this size belongs to
 * @param label       the display label for this size (e.g. "S", "M", "L")
 * @param position    the sort position among sizes of the same inventory
 * @param note        an optional note about this size
 */
public record InventorySize(int id, int inventoryId, String label, int position, String note) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<InventorySize> map() {
        return row -> new InventorySize(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("label"),
                row.getInt("position"),
                row.getString("note"));
    }
}
