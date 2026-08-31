/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents an inventory belonging to a station.
 *
 * @param id            the unique inventory identifier
 * @param stationId     the station this inventory belongs to
 * @param name          the display name of the inventory
 * @param inventoryType the type of inventory (internal, external, or mixed)
 * @param hasSizes      whether this inventory supports size variants
 * @param homogeneous   whether it holds one thing in many copies rather than a drawer of different
 *                      things. Requirements, procurements and exchanges are only offered for the
 *                      first, because none of them means anything for the second.
 */
public record Inventory(
        int id, int stationId, String name, InventoryType inventoryType, boolean hasSizes, boolean homogeneous) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Inventory> map() {
        return row -> new Inventory(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getEnum("inventory_type", InventoryType.class),
                row.getBoolean("has_sizes"),
                row.getBoolean("homogeneous"));
    }
}
