/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.lostandfound.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a lost and found item registered at a station.
 *
 * @param id          the unique item identifier
 * @param stationId   the station where the item was found
 * @param description a textual description of the item
 * @param foundAt     the date the item was found
 * @param claimedBy   the member ID who claimed the item (null if unclaimed)
 * @param claimedAt   the timestamp when the item was claimed (null if unclaimed)
 * @param createdBy   the member ID who reported the item
 * @param createdAt   the timestamp when the item was registered
 */
public record LostAndFoundItem(
        int id,
        int stationId,
        String description,
        LocalDate foundAt,
        Integer claimedBy,
        Instant claimedAt,
        int createdBy,
        Instant createdAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<LostAndFoundItem> map() {
        return row -> new LostAndFoundItem(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("description"),
                row.getObject("found_at", LocalDate.class),
                row.getObject("claimed_by") != null ? row.getInt("claimed_by") : null,
                row.get("claimed_at", INSTANT_TIMESTAMP),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
