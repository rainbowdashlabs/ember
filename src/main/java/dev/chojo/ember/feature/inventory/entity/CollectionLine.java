/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * One line of a collection: a named piece, a count of one kind of thing, or a count out of a whole
 * inventory.
 *
 * <p>Exactly one of the three targets is set, and the database refuses anything else. A named piece
 * is one piece, so a named line always asks for one.
 *
 * <p>The kind is what lets a line say "four blue ones" rather than "four out of the radio drawer",
 * which in a drawer holding radios, a charging station and a cable are two different requests. The
 * inventory target stays for the inventories that hold one thing in many copies, which carry no
 * kinds at all and never will.
 *
 * @param collectionId the collection the line belongs to
 * @param itemId       the named piece, or {@code null}
 * @param artId        the kind of thing counted, or {@code null}
 * @param inventoryId  the inventory counted out of, or {@code null}
 * @param quantity     how many pieces the line asks for, always 1 on a named line
 * @param position     the display order within the collection
 */
public record CollectionLine(
        int id, int collectionId, Integer itemId, Integer artId, Integer inventoryId, int quantity, int position) {

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
                row.getObject("art_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getInt("quantity"),
                row.getInt("position"));
    }
}
