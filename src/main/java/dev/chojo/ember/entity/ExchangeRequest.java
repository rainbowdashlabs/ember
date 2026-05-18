/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ExchangeRequest(
        int id,
        int stationId,
        int memberId,
        Integer itemId,
        int inventoryId,
        Integer oldSizeId,
        Integer newSizeId,
        Integer exchangedItemId,
        ExchangeStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        Integer createdBy) {
    public static RowMapping<ExchangeRequest> map() {
        return row -> new ExchangeRequest(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getObject("item_id", Integer.class),
                row.getInt("inventory_id"),
                row.getObject("old_size_id", Integer.class),
                row.getObject("new_size_id", Integer.class),
                row.getObject("exchanged_item_id", Integer.class),
                row.getEnum("status", ExchangeStatus.class),
                row.getString("reason"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }
}
