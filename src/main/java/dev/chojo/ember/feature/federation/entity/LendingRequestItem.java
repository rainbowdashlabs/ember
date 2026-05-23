/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * An item (or item type with quantity) within a lending request.
 */
public record LendingRequestItem(
        int id, int requestId, Integer inventoryId, Integer itemId, int quantity, Integer assignedItemId) {

    public static RowMapping<LendingRequestItem> map() {
        return row -> new LendingRequestItem(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getInt("quantity"),
                row.getObject("assigned_item_id", Integer.class));
    }
}
