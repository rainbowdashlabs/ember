/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record Procurement(
        int id,
        int stationId,
        int inventoryId,
        int memberId,
        Integer sizeId,
        String notes,
        Instant requestedAt,
        Instant fulfilledAt) {
    public static RowMapping<Procurement> map() {
        return row -> new Procurement(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("inventory_id"),
                row.getInt("member_id"),
                row.getObject("size_id", Integer.class),
                row.getString("notes"),
                row.get("requested_at", INSTANT_TIMESTAMP),
                row.get("fulfilled_at", INSTANT_TIMESTAMP));
    }
}
