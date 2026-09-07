/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.repository;

import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What goes with a piece somebody has just picked.
 *
 * <p>Two sources, in order. Everything carrying a word the picked piece carries, across the
 * inventories, which is the thing neither the inventory nor the kind could say: a radio, its charging
 * station and an antenna filed somewhere else belong together and only a word says so. Then the other
 * pieces filed beside it, in the same inventory.
 *
 * <p>Words win where both apply, and a piece is never recommended twice.
 */
@Singleton
public class EquipmentRecommendationRepository {

    /**
     * The pieces a station could bring along that go with one piece of its own.
     *
     * @param stationId the station asking
     * @param itemId    the piece that was picked
     * @param limit     how many to bring back
     * @return the recommendations, the ones sharing a word first
     */
    public List<Recommendation> forItem(int stationId, int itemId, int limit) {
        return query("""
                WITH picked AS (SELECT id, inventory_id, art_id FROM inventory_item WHERE id = :item_id),
                     shared_words AS (
                         SELECT DISTINCT other.item_id
                         FROM inventory_item_tag mine
                         JOIN inventory_item_tag other ON other.tag_id = mine.tag_id
                         WHERE mine.item_id = :item_id AND other.item_id <> :item_id
                     )
                SELECT ii.id                       AS item_id,
                       coalesce(ii.name, '')       AS item_name,
                       coalesce(ii.internal_id, '') AS internal_id,
                       ii.inventory_id,
                       inv.name                    AS inventory_name,
                       ii.art_id,
                       coalesce(art.name, '')      AS art_name,
                       (sw.item_id IS NOT NULL)    AS by_word
                FROM inventory_item ii
                JOIN inventory inv ON inv.id = ii.inventory_id
                LEFT JOIN inventory_art art ON art.id = ii.art_id
                LEFT JOIN shared_words sw ON sw.item_id = ii.id
                JOIN picked p ON TRUE
                WHERE inv.station_id = :station_id
                  AND ii.id <> :item_id
                  AND (sw.item_id IS NOT NULL OR ii.inventory_id = p.inventory_id)
                ORDER BY by_word DESC, inv.name, coalesce(art.name, ''), ii.id
                LIMIT :max_rows;""")
                .single(call().bind("station_id", stationId)
                        .bind("item_id", itemId)
                        .bind("max_rows", limit))
                .map(row -> new Recommendation(
                        row.getInt("item_id"),
                        row.getString("item_name"),
                        row.getString("internal_id"),
                        row.getInt("inventory_id"),
                        row.getString("inventory_name"),
                        row.getObject("art_id", Integer.class),
                        row.getString("art_name"),
                        row.getBoolean("by_word")))
                .all();
    }

    /**
     * One piece that goes with another.
     *
     * @param byWord whether it was found through a shared word rather than through the shelf it is on
     */
    public record Recommendation(
            int itemId,
            String itemName,
            String internalId,
            int inventoryId,
            String inventoryName,
            Integer artId,
            String artName,
            boolean byWord) {}
}
