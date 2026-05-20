/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a historical assignment record tracking when an item was given to and returned by a member.
 *
 * @param id         the unique history entry identifier
 * @param itemId     the inventory item this history entry refers to
 * @param memberId   the member the item was assigned to, or {@code null} if unknown
 * @param memberName the name of the member at the time of assignment
 * @param givenOut   when the item was given out
 * @param returned   when the item was returned, or {@code null} if still assigned
 */
public record InventoryItemHistory(
        int id, int itemId, Integer memberId, String memberName, Instant givenOut, Instant returned) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<InventoryItemHistory> map() {
        return row -> new InventoryItemHistory(
                row.getInt("id"),
                row.getInt("item_id"),
                row.getObject("member_id", Integer.class),
                row.getString("member_name"),
                row.get("given_out", INSTANT_TIMESTAMP),
                row.get("returned", INSTANT_TIMESTAMP));
    }
}
