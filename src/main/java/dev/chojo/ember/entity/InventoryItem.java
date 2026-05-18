/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record InventoryItem(
        int id,
        int inventoryId,
        String internalId,
        String name,
        Integer sizeId,
        String metadata,
        Integer assignedTo,
        Instant lostAt,
        ItemSource itemSource) {
    public static RowMapping<InventoryItem> map() {
        return row -> new InventoryItem(
                row.getInt("id"),
                row.getInt("inventory_id"),
                row.getString("internal_id"),
                row.getString("name"),
                row.getObject("size_id", Integer.class),
                row.getString("metadata"),
                row.getObject("assigned_to", Integer.class),
                row.get("lost_at", INSTANT_TIMESTAMP),
                row.getEnum("item_source", ItemSource.class));
    }

    public Optional<Integer> sizeIdOpt() {
        return Optional.ofNullable(sizeId);
    }

    public Optional<Integer> assignedToOpt() {
        return Optional.ofNullable(assignedTo);
    }

    public enum ItemSource {
        INTERNAL,
        EXTERNAL
    }
}
