/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record InventoryItemHistory(
        int id, int itemId, Integer memberId, String memberName, Instant givenOut, Instant returned) {
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
