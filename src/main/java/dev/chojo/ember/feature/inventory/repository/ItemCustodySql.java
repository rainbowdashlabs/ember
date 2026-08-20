/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

/**
 * The one place that says which items belong to a station's lists.
 *
 * <p>A station used to see the items in its own inventories, which was the same question as
 * ownership because an inventory belonged to exactly one station and a row never left it. Custody
 * splits the two: a station holds gear the body above it owns, and it stops holding gear it has
 * posted back even though the definition still lives in its inventory. What a station sees is what
 * it has, not what it owns.
 *
 * <p>An item is in a station's custody when any of these hold:
 *
 * <ul>
 *   <li>it rests with its owner and that owner is the station,
 *   <li>a station that does not own it holds it, and that station is this one,
 *   <li>a member or a federation partner holds it through this station,
 *   <li>it is in transit on a movement this station is one end of,
 *   <li>it is lost and was last held here.
 * </ul>
 *
 * <p>The first case is the only one that reads the inventory rather than the item: an item resting
 * with its owner names no station, because for a station's own gear the owner is the station.
 *
 * <p>The in-transit case is named in that list and is not written below, because there is no
 * movement table for it to reach into yet. Nothing can currently be in transit, so the predicate is
 * complete for the data that exists; the clause belongs here rather than in any caller, and is
 * added by the change that creates movements.
 *
 * <p>Every expression here is a compile-time constant. The station always travels as a named bind.
 */
public final class ItemCustodySql {

    /**
     * The bind name every predicate below expects, so a caller cannot pick a different one and have
     * the predicate silently match nothing.
     */
    public static final String STATION_BIND = "custody_station";

    private ItemCustodySql() {}

    /**
     * A predicate matching the items a station holds.
     *
     * @param itemAlias      the alias of {@code inventory_item} in the query, e.g. {@code ii}
     * @param inventoryAlias the alias of the joined {@code inventory} row, needed for gear resting
     *                       with a station that owns it
     * @return the SQL predicate, expecting {@code :custody_station} to be bound
     */
    public static String heldBy(String itemAlias, String inventoryAlias) {
        return """
                (
                    (%1$s.custody = 'WITH_OWNER' AND %1$s.owner_kind = 'STATION' AND %2$s.station_id = :%3$s)
                    OR (%1$s.custody IN ('AT_STATION', 'WITH_MEMBER', 'WITH_PARTNER', 'LOST')
                        AND %1$s.custody_station_id = :%3$s)
                )""".formatted(itemAlias, inventoryAlias, STATION_BIND);
    }

    /**
     * The join that {@link #heldBy(String, String)} needs, for queries that do not already have the
     * inventory row to hand.
     *
     * @param itemAlias      the alias of {@code inventory_item}
     * @param inventoryAlias the alias to give the joined {@code inventory} row
     * @return the JOIN clause
     */
    public static String joinInventory(String itemAlias, String inventoryAlias) {
        return "JOIN inventory %2$s ON %2$s.id = %1$s.inventory_id".formatted(itemAlias, inventoryAlias);
    }
}
