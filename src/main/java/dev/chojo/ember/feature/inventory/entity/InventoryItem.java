/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an individual item within an inventory that can be assigned to members.
 *
 * @param id          the unique item identifier
 * @param inventoryId the inventory this item belongs to
 * @param internalId  an internal identifier for the item (e.g. serial number)
 * @param name        the display name of the item
 * @param sizeId      the size variant of the item, or {@code null} if not applicable
 * @param metadata    JSON metadata associated with the item
 * @param assignedTo  the member this item is assigned to, or {@code null} if unassigned
 * @param lostAt      when the item was marked as lost, or {@code null} if not lost
 * @param itemSource  whether the item is internally or externally sourced
 */
public record InventoryItem(
        int id,
        int inventoryId,
        String internalId,
        String name,
        Integer sizeId,
        String metadata,
        Integer assignedTo,
        Instant lostAt,
        ItemSource itemSource) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryItem> map() {
        return row -> new InventoryItem(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("internal_id"),
                row.getString("name"),
                row.getObject("size_id", Integer.class),
                row.getString("metadata"),
                row.getObject("assigned_to", Integer.class),
                row.get("lost_at", INSTANT_TIMESTAMP),
                row.getEnum("item_source", ItemSource.class));
    }

    /**
     * Indicates the origin of an inventory item.
     */
    public enum ItemSource {
        /**
         * The item is owned by the organization.
         */
        INTERNAL,
        /**
         * The item is owned by the member.
         */
        EXTERNAL
    }
}
