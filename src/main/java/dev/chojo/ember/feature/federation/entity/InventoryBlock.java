/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Blocks lending of inventory items during a specific date range.
 */
public record InventoryBlock(
        int id,
        int stationId,
        @Nullable Integer inventoryId,
        @Nullable Integer itemId,
        LocalDate blockFrom,
        LocalDate blockTo,
        String reason) {

    public static RowMapping<InventoryBlock> map() {
        return row -> new InventoryBlock(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("inventory_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getObject("block_from", LocalDate.class),
                row.getObject("block_to", LocalDate.class),
                row.getString("reason"));
    }

    /**
     * Whether this block covers the station, the inventory or the piece: a station-wide block covers
     * everything, an inventory block its inventory, a piece block its piece.
     *
     * @param inventoryId the inventory asked about, or {@code null}
     * @param itemId      the piece asked about, or {@code null}
     * @return {@code true} when it covers it
     */
    public boolean covers(@Nullable Integer inventoryId, @Nullable Integer itemId) {
        if (this.inventoryId == null && this.itemId == null) return true;
        if (this.inventoryId != null && this.inventoryId.equals(inventoryId) && this.itemId == null) return true;
        return this.itemId != null && this.itemId.equals(itemId);
    }
}
