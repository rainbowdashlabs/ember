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
        return heldByStation(itemAlias, inventoryAlias, ":" + STATION_BIND);
    }

    /**
     * A predicate matching the stock of one inventory: what the station that inventory belongs to
     * actually holds of it, minus what is in the post.
     *
     * <p>Being written down in an inventory is not the same as being there. Gear the body above the
     * station owns and has taken back rests with its owner, and the row stays where it is because the
     * history of the piece hangs on it; counting that row as stock says the station has something
     * that went back to the association last month. Which station to ask about is the one the
     * inventory belongs to, so this needs no bind of its own.
     *
     * <p>A piece on its way somewhere is left out for the older reason: it is at neither end, and
     * every figure drawn from the list would inherit that.
     *
     * @param itemAlias      the alias of {@code inventory_item}
     * @param inventoryAlias the alias of the joined {@code inventory} row
     * @return the SQL predicate, needing no bind of its own
     */
    public static String stockOf(String itemAlias, String inventoryAlias) {
        return "(%s AND %s.custody <> 'IN_TRANSIT')"
                .formatted(heldByStation(itemAlias, inventoryAlias, inventoryAlias + ".station_id"), itemAlias);
    }

    /**
     * The one formulation of "this station holds this piece", with the station left open so it can be
     * a bind in one place and the inventory's own station in another. Writing it twice is how the two
     * drifted apart before.
     */
    private static String heldByStation(String itemAlias, String inventoryAlias, String station) {
        return """
                (
                    (%1$s.custody = 'WITH_OWNER' AND %1$s.owner_kind = 'STATION' AND %2$s.station_id = %3$s)
                    OR (%1$s.custody IN ('AT_STATION', 'WITH_MEMBER', 'WITH_PARTNER', 'LOST')
                        AND %1$s.custody_station_id = %3$s)
                    OR (%1$s.custody = 'IN_TRANSIT' AND EXISTS(
                        SELECT 1 FROM item_movement mv
                        WHERE mv.id = %1$s.custody_movement_id AND mv.station_id = %3$s))
                )""".formatted(itemAlias, inventoryAlias, station);
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

    /**
     * A predicate matching the free stock of the station an inventory belongs to: the items that
     * station holds and that nobody has.
     *
     * <p>Resting with the owner only counts when the station is the owner. Gear the body above owns
     * that has gone back to it is resting too, and it is not the station's to hand out. Gear in the
     * post, gear a partner has and gear nobody can find are out for the same reason, which is the
     * whole point of storing custody rather than reading it off the assignment.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, needing no bind of its own
     */
    public static String freeStock(String itemAlias) {
        return "(%1$s.custody = 'AT_STATION' OR (%1$s.custody = 'WITH_OWNER' AND %1$s.owner_kind = 'STATION'))"
                .formatted(itemAlias);
    }

    /**
     * A predicate matching the gear a station can actually put its hands on.
     *
     * <p>Narrower than {@link #heldBy(String, String)} and wider than {@link #freeStock(String)}, and
     * it exists because neither of those answers "can this station bring the thing along". Held-by
     * counts gear that is lost or in the post, which cannot be brought. Free stock leaves out gear a
     * member keeps, and radios permanently handed to a group leader are the ordinary case rather than
     * the exception: a list that reports them missing because somebody at the station is holding them
     * is worse than no list.
     *
     * <p>Gear with a federation partner is out for the same reason lost gear is: it is somewhere else.
     *
     * @param itemAlias      the alias of {@code inventory_item}
     * @param inventoryAlias the alias of the joined {@code inventory} row
     * @return the SQL predicate, expecting {@code :custody_station} to be bound
     */
    public static String atHand(String itemAlias, String inventoryAlias) {
        return """
                (
                    (%1$s.custody = 'WITH_OWNER' AND %1$s.owner_kind = 'STATION' AND %2$s.station_id = :%3$s)
                    OR (%1$s.custody IN ('AT_STATION', 'WITH_MEMBER') AND %1$s.custody_station_id = :%3$s)
                )""".formatted(itemAlias, inventoryAlias, STATION_BIND);
    }
}
