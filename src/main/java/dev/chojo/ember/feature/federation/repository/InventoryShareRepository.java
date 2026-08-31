/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import dev.chojo.ember.feature.federation.entity.InventoryShare;
import dev.chojo.ember.feature.federation.entity.ShareGrant;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

/**
 * Data access for what a station puts on offer to its lending partners. One row per inventory and
 * one per item, so writing a share twice for the same gear overwrites rather than piles up.
 */
@Singleton
public class InventoryShareRepository {

    public List<InventoryShare> findByStation(int stationId) {
        return query("""
                SELECT %s FROM federation_inventory_share
                WHERE station_id = :station_id
                ORDER BY inventory_id NULLS LAST, item_id NULLS LAST;""", InventoryShare.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(InventoryShare.map())
                .all();
    }

    public Optional<InventoryShare> findForInventory(int stationId, int inventoryId) {
        return query("""
                SELECT %s FROM federation_inventory_share
                WHERE station_id = :station_id AND inventory_id = :inventory_id;""", InventoryShare.COLUMNS)
                .single(call().bind("station_id", stationId).bind("inventory_id", inventoryId))
                .map(InventoryShare.map())
                .first();
    }

    public Optional<InventoryShare> findForItem(int stationId, int itemId) {
        return query("""
                SELECT %s FROM federation_inventory_share
                WHERE station_id = :station_id AND item_id = :item_id;""", InventoryShare.COLUMNS)
                .single(call().bind("station_id", stationId).bind("item_id", itemId))
                .map(InventoryShare.map())
                .first();
    }

    public InventoryShare upsertInventoryShare(int stationId, int inventoryId, ShareScope scope, ShareGrant grant) {
        return insertReturning(
                """
                INSERT INTO federation_inventory_share(station_id, inventory_id, share_scope, share_grant)
                VALUES (:station_id, :inventory_id, :share_scope, :share_grant)
                ON CONFLICT (station_id, inventory_id) WHERE inventory_id IS NOT NULL
                DO UPDATE SET share_scope = :share_scope, share_grant = :share_grant
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("inventory_id", inventoryId)
                        .bind("share_scope", scope)
                        .bind("share_grant", grant),
                InventoryShare.map(),
                InventoryShare.COLUMNS);
    }

    public InventoryShare upsertItemShare(int stationId, int itemId, ShareScope scope, ShareGrant grant) {
        return insertReturning(
                """
                INSERT INTO federation_inventory_share(station_id, item_id, share_scope, share_grant)
                VALUES (:station_id, :item_id, :share_scope, :share_grant)
                ON CONFLICT (station_id, item_id) WHERE item_id IS NOT NULL
                DO UPDATE SET share_scope = :share_scope, share_grant = :share_grant
                RETURNING %s;""",
                call().bind("station_id", stationId)
                        .bind("item_id", itemId)
                        .bind("share_scope", scope)
                        .bind("share_grant", grant),
                InventoryShare.map(),
                InventoryShare.COLUMNS);
    }

    public boolean deleteInventoryShare(int stationId, int inventoryId) {
        return query("""
                DELETE FROM federation_inventory_share
                WHERE station_id = :station_id AND inventory_id = :inventory_id;""")
                .single(call().bind("station_id", stationId).bind("inventory_id", inventoryId))
                .delete()
                .changed();
    }

    public boolean deleteItemShare(int stationId, int itemId) {
        return query("""
                DELETE FROM federation_inventory_share
                WHERE station_id = :station_id AND item_id = :item_id;""")
                .single(call().bind("station_id", stationId).bind("item_id", itemId))
                .delete()
                .changed();
    }

    /**
     * Replaces the partners one share is aimed at.
     *
     * <p>Only read when the share's scope is {@code SPECIFIC}. An empty list with that scope reaches
     * nobody, which is a thing somebody may legitimately write: it is how a whole inventory is taken
     * off the market without deleting the row that says what it was.
     *
     * @param shareId    the share
     * @param partnerIds the partnerships it is for
     */
    public void setTargets(int shareId, List<Integer> partnerIds) {
        query("DELETE FROM federation_inventory_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .delete();
        for (int partnerId : partnerIds) {
            query("""
                    INSERT INTO federation_inventory_share_target(share_id, partner_id)
                    VALUES (:share_id, :partner_id);""")
                    .single(call().bind("share_id", shareId).bind("partner_id", partnerId))
                    .insert();
        }
    }

    /** The partners one share is aimed at, empty when it is for everybody. */
    public List<Integer> findTargets(int shareId) {
        return query("SELECT partner_id FROM federation_inventory_share_target WHERE share_id = :share_id;")
                .single(call().bind("share_id", shareId))
                .map(row -> row.getInt("partner_id"))
                .all();
    }

    /**
     * Every target row of every share a station holds, so a resolution over a whole station reads
     * the targets once instead of once per share.
     */
    public List<ShareTarget> findTargetsByStation(int stationId) {
        return query("""
                SELECT t.share_id, t.partner_id
                FROM federation_inventory_share_target t
                    JOIN federation_inventory_share s ON s.id = t.share_id
                WHERE s.station_id = :station_id;""")
                .single(call().bind("station_id", stationId))
                .map(row -> new ShareTarget(row.getInt("share_id"), row.getInt("partner_id")))
                .all();
    }

    /** One partner a share is aimed at. */
    public record ShareTarget(int shareId, int partnerId) {}
}
