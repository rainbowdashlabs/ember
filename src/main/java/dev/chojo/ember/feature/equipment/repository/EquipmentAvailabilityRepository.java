/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.inventory.entity.LineTarget;
import dev.chojo.ember.feature.inventory.entity.ResolvedTarget;
import dev.chojo.ember.feature.inventory.repository.ItemCustodySql;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The raw reads behind one definition of free: the stock a station could bring along, and the two
 * claims on it that already live in the database.
 *
 * <p>The third claim, an appointment's own need, is never a row while it is merely planned and is
 * derived from the recurrence rule instead, which is why it is not here.
 */
@Singleton
public class EquipmentAvailabilityRepository {

    /**
     * How many pieces of one target a station could put its hands on.
     *
     * <p>The predicate is {@link ItemCustodySql#atHand(String, String)} rather than free stock, for the
     * same reason a collection is read with it: radios permanently handed to a group leader are the
     * ordinary case, and a list reporting them missing because somebody at the station is holding them
     * is worse than no list. Gear that is lost, in the post or with a partner is out, because it cannot
     * be brought along.
     *
     * @param stationId the station asking
     * @param target    what is being asked about
     * @return the count
     */
    public int stockOf(int stationId, LineTarget target) {
        return SqlSupport.count(
                """
                SELECT count(*) AS cnt FROM inventory_item ii
                %1$s
                WHERE (ii.id = :item_id::INT OR ii.art_id = :art_id::INT OR ii.inventory_id = :inventory_id::INT)
                  AND %2$s;""",
                call().bind("item_id", target.itemId())
                        .bind("art_id", target.artId())
                        .bind("inventory_id", target.inventoryId())
                        .bind(ItemCustodySql.STATION_BIND, stationId),
                ItemCustodySql.joinInventory("ii", "iinv"),
                ItemCustodySql.atHand("ii", "iinv"));
    }

    /**
     * The pieces of one target a station could put its hands on, in a stable order.
     *
     * @param stationId the station asking
     * @param target    what is being asked about
     * @return the piece IDs
     */
    public List<Integer> piecesOf(int stationId, LineTarget target) {
        return query("""
                SELECT ii.id FROM inventory_item ii
                %1$s
                WHERE (ii.id = :item_id::INT OR ii.art_id = :art_id::INT OR ii.inventory_id = :inventory_id::INT)
                  AND %2$s
                ORDER BY ii.id;""", ItemCustodySql.joinInventory("ii", "iinv"), ItemCustodySql.atHand("ii", "iinv"))
                .single(call().bind("item_id", target.itemId())
                        .bind("art_id", target.artId())
                        .bind("inventory_id", target.inventoryId())
                        .bind(ItemCustodySql.STATION_BIND, stationId))
                .map(row -> row.getInt("id"))
                .all();
    }

    /**
     * Fills in the levels above a target and reads its name.
     *
     * @param target what to resolve
     * @return the resolved target, or empty when it points at something that no longer exists
     */
    public Optional<ResolvedTarget> resolve(LineTarget target) {
        return query("""
                SELECT it.id                                     AS item_id,
                       coalesce(it.art_id, art.id)               AS art_id,
                       coalesce(it.inventory_id, art.inventory_id, inv.id) AS inventory_id,
                       coalesce(it.name, art.name, inv.name, '') AS label
                FROM (SELECT 1) one
                LEFT JOIN inventory_item it ON it.id = :item_id::INT
                LEFT JOIN inventory_art art ON art.id = coalesce(:art_id::INT, it.art_id)
                LEFT JOIN inventory inv ON inv.id = coalesce(:inventory_id::INT, it.inventory_id, art.inventory_id);""")
                .single(call().bind("item_id", target.itemId())
                        .bind("art_id", target.artId())
                        .bind("inventory_id", target.inventoryId()))
                .map(resolvedTarget())
                .first()
                .filter(resolved ->
                        resolved.itemId() != null || resolved.artId() != null || resolved.inventoryId() != null);
    }

    /**
     * What a partner station has been promised or already has, over a window.
     *
     * <p>A line with pieces set aside claims those pieces, one row each. A line still asking for a
     * count claims that count out of whatever it named, which is the honest reading before anybody has
     * picked.
     *
     * @param stationId the station whose gear it is
     * @param from      the first day of the window
     * @param to        the last day of the window, or {@code null} to reach forward without an end
     * @return the loans overlapping the window
     */
    public List<LoanClaim> loanClaims(int stationId, LocalDate from, LocalDate to) {
        return query("""
                SELECT ri.id                    AS request_item_id,
                       ri.quantity,
                       a.item_id                AS assigned_item_id,
                       ri.item_id,
                       ri.art_id,
                       ri.inventory_id,
                       r.requested_date_from    AS date_from,
                       r.requested_date_to      AS date_to,
                       coalesce(s.name, '')     AS partner_name
                FROM federation_lending_request_item ri
                JOIN federation_lending_request r ON r.id = ri.request_id
                JOIN station owner ON owner.uid = r.owning_station_uid
                LEFT JOIN federation_lending_request_item_assignment a ON a.request_item_id = ri.id
                LEFT JOIN station s ON s.uid = r.requesting_station_uid
                WHERE owner.id = :station_id
                  AND r.status IN ('APPROVED', 'LENT')
                  AND (:date_to::DATE IS NULL OR r.requested_date_from <= :date_to::DATE)
                  AND (r.requested_date_to IS NULL OR :date_from::DATE IS NULL
                       OR r.requested_date_to >= :date_from::DATE)
                ORDER BY ri.id, a.item_id;""")
                .single(call().bind("station_id", stationId)
                        .bind("date_from", from)
                        .bind("date_to", to))
                .map(row -> new LoanClaim(
                        row.getInt("request_item_id"),
                        row.getInt("quantity"),
                        row.getObject("assigned_item_id", Integer.class),
                        row.getObject("item_id", Integer.class),
                        row.getObject("art_id", Integer.class),
                        row.getObject("inventory_id", Integer.class),
                        row.getObject("date_from", LocalDate.class),
                        row.getObject("date_to", LocalDate.class),
                        row.getString("partner_name")))
                .all();
    }

    /**
     * The periods a station has set aside on purpose, over a window.
     *
     * @param stationId the station
     * @param from      the first day of the window
     * @param to        the last day of the window, or {@code null} to reach forward without an end
     * @return the blocks overlapping the window
     */
    public List<BlockClaim> blockClaims(int stationId, LocalDate from, LocalDate to) {
        return query("""
                SELECT b.id, b.inventory_id, b.item_id, b.block_from, b.block_to, coalesce(b.reason, '') AS reason
                FROM federation_inventory_block b
                WHERE b.station_id = :station_id
                  AND (:date_to::DATE IS NULL OR b.block_from <= :date_to::DATE)
                  AND (:date_from::DATE IS NULL OR b.block_to >= :date_from::DATE)
                ORDER BY b.block_from, b.id;""")
                .single(call().bind("station_id", stationId)
                        .bind("date_from", from)
                        .bind("date_to", to))
                .map(row -> new BlockClaim(
                        row.getInt("id"),
                        row.getObject("inventory_id", Integer.class),
                        row.getObject("item_id", Integer.class),
                        row.getObject("block_from", LocalDate.class),
                        row.getObject("block_to", LocalDate.class),
                        row.getString("reason")))
                .all();
    }

    /**
     * What is here on loan against one line of an appointment's needs.
     *
     * <p>A borrowed piece is an ordinary row at the borrowing station, so it counts towards the need
     * like any of the station's own pieces do. That is what makes "fourteen needed, ten our own, four
     * borrowed" one answer rather than two.
     *
     * @param needId the line
     * @return how many borrowed pieces are here against it
     */
    public int borrowedAgainstNeed(int needId) {
        return SqlSupport.count("""
                SELECT count(*) AS cnt FROM inventory_item ii
                JOIN federation_lending_request_item ri ON ri.id = ii.loan_request_item_id
                WHERE ri.need_id = :need_id AND ii.owner_kind = 'PARTNER_STATION';""", call().bind("need_id", needId));
    }

    /**
     * How many pieces have been asked of a partner against one line and have not arrived yet.
     *
     * @param needId the line
     * @return the outstanding count
     */
    public int outstandingAgainstNeed(int needId) {
        return SqlSupport.count("""
                SELECT coalesce(sum(ri.quantity), 0) AS cnt
                FROM federation_lending_request_item ri
                JOIN federation_lending_request r ON r.id = ri.request_id
                WHERE ri.need_id = :need_id AND r.status IN ('REQUESTED', 'APPROVED');""", call().bind("need_id", needId));
    }

    private static RowMapping<ResolvedTarget> resolvedTarget() {
        return row -> new ResolvedTarget(
                row.getObject("item_id", Integer.class),
                row.getObject("art_id", Integer.class),
                row.getObject("inventory_id", Integer.class),
                row.getString("label"));
    }

    /**
     * One line of a lending request, as the availability arithmetic reads it.
     *
     * @param assignedItemId the piece set aside, or {@code null} while the line is still a count
     */
    public record LoanClaim(
            int requestItemId,
            int quantity,
            Integer assignedItemId,
            Integer itemId,
            Integer artId,
            Integer inventoryId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String partnerName) {}

    /**
     * One period a station has set aside, as the availability arithmetic reads it. A block naming
     * neither an inventory nor a piece holds everything the station has.
     */
    public record BlockClaim(
            int id, Integer inventoryId, Integer itemId, LocalDate blockFrom, LocalDate blockTo, String reason) {}
}
