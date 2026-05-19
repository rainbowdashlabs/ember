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

public record LostAndFoundItem(
        int id,
        int stationId,
        String description,
        LocalDate foundAt,
        boolean hasImage,
        Integer claimedBy,
        Instant claimedAt,
        int createdBy,
        Instant createdAt) {

    public static RowMapping<LostAndFoundItem> map() {
        return row -> new LostAndFoundItem(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("description"),
                row.getObject("found_at", LocalDate.class),
                row.getBytes("image") != null,
                row.getObject("claimed_by") != null ? row.getInt("claimed_by") : null,
                row.get("claimed_at", INSTANT_TIMESTAMP),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
