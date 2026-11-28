/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

/**
 * The one place that defines which items belong to a station's lists.
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
     * history of the piece hangs on it; counting that row as stock shows that the station has something
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
     * A predicate matching the items no open movement carries: neither named as setting out or as
     * promised, nor among the further pieces of a dispatch that sends many at once.
     *
     * <p>Such a piece is not free to be offered for anything else, however free its custody looks.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, needing no bind of its own
     */
    public static String notOnAMovement(String itemAlias) {
        return """
                (NOT EXISTS (SELECT 1 FROM item_movement open_movement WHERE open_movement.state = 'OPEN' \
                AND %1$s.id IN (open_movement.outgoing_item_id, open_movement.incoming_item_id)) \
                AND NOT EXISTS (SELECT 1 FROM item_movement_item carried \
                JOIN item_movement carrying ON carrying.id = carried.movement_id \
                WHERE carrying.state = 'OPEN' AND carried.item_id = %1$s.id))""".formatted(itemAlias);
    }

    /**
     * A predicate matching the items no loan to a partner station holds today.
     *
     * <p>A piece is out only while it is gone: an agreed loan holds it from its first day to its last,
     * nothing before, and gear handed over is held until it is back, whatever the days said.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, needing no bind of its own
     */
    public static String notSetAsideForALoan(String itemAlias) {
        return loanHold(itemAlias, "current_date", "current_date", "");
    }

    /**
     * The same over the days of one loan, leaving that loan out, which is the loan asking about its own
     * pieces: another loan holds a piece when it holds it on any of those days.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, expecting {@code :own_loan} to be bound to the request id
     */
    public static String notSetAsideForAnotherLoan(String itemAlias) {
        return loanHold(
                itemAlias,
                "(SELECT own.requested_date_from FROM federation_lending_request own WHERE own.id = :own_loan)",
                "(SELECT own.requested_date_to FROM federation_lending_request own WHERE own.id = :own_loan)",
                " AND loan.id <> :own_loan");
    }

    private static String loanHold(String itemAlias, String firstDay, String lastDay, String except) {
        return """
                NOT EXISTS (SELECT 1 FROM federation_lending_request_item_assignment loan_piece \
                JOIN federation_lending_request_item loan_line ON loan_line.id = loan_piece.request_item_id \
                JOIN federation_lending_request loan ON loan.id = loan_line.request_id \
                WHERE loan_piece.item_id = %1$s.id AND %2$s%3$s)""".formatted(itemAlias, loanHoldsOn("loan", firstDay, lastDay), except);
    }

    /**
     * A condition on a lending request matching a loan that holds its pieces on any day of a span: gear
     * handed over until it is back, whatever the days said, and agreed gear on its own days only.
     *
     * @param loanAlias the alias of {@code federation_lending_request}
     * @param firstDay  the first day of the span, as SQL
     * @param lastDay   the last day of the span, as SQL
     * @return the SQL condition
     */
    public static String loanHoldsOn(String loanAlias, String firstDay, String lastDay) {
        return """
                ((%1$s.status = 'LENT' AND greatest(%1$s.requested_date_to, current_date) >= %2$s) \
                OR (%1$s.status = 'APPROVED' AND %1$s.requested_date_from <= %3$s \
                AND %1$s.requested_date_to >= %2$s))""".formatted(loanAlias, firstDay, lastDay);
    }

    /**
     * A predicate matching the items a station can lend over some days, before asking which days: on the
     * shelf, or out with a partner on a loan and coming back, and carried by no open movement. Whether a
     * piece is free on a given day is the claims' question.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, needing no bind of its own
     */
    public static String lendable(String itemAlias) {
        return "((%2$s OR %3$s) AND %4$s)"
                .formatted(itemAlias, freeStock(itemAlias), lentOut(itemAlias), notOnAMovement(itemAlias));
    }

    /**
     * A predicate matching the items out with a partner station on one of the station's own loans, which
     * come back when the loan is checked back. A piece with a partner for any other reason is not.
     *
     * @param itemAlias the alias of {@code inventory_item}, or the bare table name
     * @return the SQL predicate, needing no bind of its own
     */
    public static String lentOut(String itemAlias) {
        return """
                (%1$s.custody = 'WITH_PARTNER' AND EXISTS (SELECT 1 \
                FROM federation_lending_request_item_assignment out_piece \
                JOIN federation_lending_request_item out_line ON out_line.id = out_piece.request_item_id \
                JOIN federation_lending_request out_loan ON out_loan.id = out_line.request_id \
                WHERE out_piece.item_id = %1$s.id AND out_loan.status = 'LENT'))""".formatted(itemAlias);
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
     * <p>Gear the station lent to a partner station on a loan counts: the loan's claim takes it out for
     * the days it is gone, until it is back, so the stock does not take it out a second time. Gear a
     * partner keeps for any other reason is out for the same reason lost gear is: it is somewhere else.
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
                    OR (%1$s.owner_kind = 'STATION' AND %2$s.station_id = :%3$s AND %4$s)
                )""".formatted(itemAlias, inventoryAlias, STATION_BIND, lentOut(itemAlias));
    }
}
