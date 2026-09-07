/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.util.Locale;

/**
 * A kind of thing inside one inventory, sitting between the inventory and the individual piece.
 *
 * <p>A drawer holding six blue radios, five green ones, four orange ones, a charging station and a
 * cable is eighteen pieces over six kinds. Without this level there is no row for "the blue ones",
 * so nothing can count them, reserve against them, share them with a partner or ask for four of
 * them. The kind is that row.
 *
 * <p>It never replaces the piece's name. {@code Pager 01} is a piece of the kind {@code Pager}, and
 * both readings are wanted at once.
 *
 * <p>Kinds exist only in inventories that hold a drawer of different things. An inventory of one
 * thing in many copies is structured by its sizes instead.
 *
 * @param inventoryId the inventory this kind belongs to
 * @param id          the unique identifier
 * @param name        what the station calls it, in its own spelling
 * @param note        a free note, empty when nobody wrote one
 * @param position    the sort position among the kinds of the same inventory
 * @param mergeKey    the name trimmed and lowered, maintained by the database, which is what makes
 *                    two stations using the same word mean the same kind
 */
public record InventoryArt(int id, int inventoryId, String name, String note, int position, String mergeKey) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryArt> map() {
        return row -> new InventoryArt(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("name"),
                row.getString("note"),
                row.getInt("position"),
                row.getString("merge_key"));
    }

    /**
     * The key two stations compare on, computed the same way the database computes it.
     *
     * <p>The stored column is the authority; this is for the checks that happen before a row exists,
     * such as refusing a second kind that differs from the first only in spacing or case.
     *
     * @param name a kind's name
     * @return the name trimmed and lowered, or an empty string when there is no name
     */
    public static String mergeKeyOf(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
