/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One line of a collection: either a named piece or a count out of an inventory.
 *
 * <p>Exactly one of the two targets is set, and the database refuses anything else. A named piece is
 * one piece, so a named line always asks for one.
 *
 * @param collectionId the collection the line belongs to
 * @param itemId       the named piece, or {@code null} on a counted line
 * @param inventoryId  the inventory a counted line draws from, or {@code null} on a named line
 * @param quantity     how many pieces the line asks for, always 1 on a named line
 * @param position     the display order within the collection
 */
public record CollectionLine(
        int id, int collectionId, Integer itemId, Integer inventoryId, int quantity, int position) {

    /**
     * Whether this line names one piece rather than asking for a count.
     *
     * @return {@code true} when the line names a piece
     */
    public boolean namesItem() {
        return itemId != null;
    }

    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return the mapping
     */
    public static RowMapping<CollectionLine> map() {
        return row -> new CollectionLine(
                row.getInt("id"),
                row.getInt("collection_id"),
                row.getObject("item_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getInt("quantity"),
                row.getInt("position"));
    }
}
