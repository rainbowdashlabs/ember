/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.inventory.entity.StepSubject;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The pieces one movement carries, for a dispatch that sends many at once.
 *
 * <p>A movement still names one outgoing and one incoming item, which is what everything reading a movement
 * points at. This is the rest of the load, and it is empty for every movement about a single piece.
 */
@Singleton
public class ItemMovementItemRepository {

    /**
     * Records the pieces a movement carries on one of its legs.
     *
     * @param movementId the movement
     * @param subject    which leg they are on
     * @param itemIds    the pieces, which may be empty
     */
    public void add(int movementId, StepSubject subject, List<Integer> itemIds) {
        if (itemIds.isEmpty()) return;
        query("""
                INSERT INTO item_movement_item(movement_id, item_id, subject)
                SELECT :movement_id, unnest(:item_ids), :subject
                ON CONFLICT DO NOTHING;""")
                .single(call().bind("movement_id", movementId)
                        .bind("item_ids", itemIds, PostgreSqlTypes.INTEGER)
                        .bind("subject", subject))
                .insert();
    }

    /**
     * The pieces a movement carries on one leg, the named one excluded: it is carried by the movement row
     * itself and everything that walks a step reaches it there.
     *
     * @param movementId the movement
     * @param subject    which leg to read
     * @return the item ids, in the order they were added
     */
    public List<Integer> findItems(int movementId, StepSubject subject) {
        return query("""
                SELECT item_id FROM item_movement_item
                WHERE movement_id = :movement_id AND subject = :subject
                ORDER BY item_id;""")
                .single(call().bind("movement_id", movementId).bind("subject", subject))
                .map(row -> row.getInt("item_id"))
                .all();
    }
}
