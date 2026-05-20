/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Defines how many items from a specific inventory are required for members with a given role or group.
 *
 * @param id          the unique requirement identifier
 * @param inventoryId the inventory this requirement applies to
 * @param roleId      the role this requirement targets, or 0 if not role-based
 * @param groupId     the group this requirement targets, or 0 if not group-based
 * @param quantity    the number of items required
 * @param position    the sort position for display ordering
 */
public record InventoryRequirement(int id, int inventoryId, int roleId, int groupId, int quantity, int position) {
    /** Creates a row mapping for database result set conversion. */
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
