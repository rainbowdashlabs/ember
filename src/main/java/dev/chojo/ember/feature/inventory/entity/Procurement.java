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
 * Represents an equipment procurement request for a member.
 *
 * @param id          the unique procurement identifier
 * @param stationId   the station this procurement belongs to
 * @param inventoryId the inventory the procurement is for
 * @param memberId    the member the equipment is being procured for, or {@code null} for an order a
 *                    cluster places for its own store
 * @param sizeId      the requested size, or {@code null} if not applicable
 * @param notes       additional notes for the procurement
 * @param requestedAt when the procurement was requested
 * @param fulfilledAt when the procurement was fulfilled, or {@code null} if still open
 */
public record Procurement(
        int id,
        int stationId,
        int inventoryId,
        Integer memberId,
        Integer sizeId,
        String notes,
        Instant requestedAt,
        Instant fulfilledAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<Procurement> map() {
        return row -> new Procurement(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("inventory_id"),
                row.getObject("member_id", Integer.class),
                row.getObject("size_id", Integer.class),
                row.getString("notes"),
                row.get("requested_at", INSTANT_TIMESTAMP),
                row.get("fulfilled_at", INSTANT_TIMESTAMP));
    }
}
