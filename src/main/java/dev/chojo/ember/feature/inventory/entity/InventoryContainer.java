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
 * Station-scoped storage location. Every room, drawer, shelf, and box is one
 * row; {@code parentId} forms a forest of trees.
 *
 * @param id          the unique container identifier
 * @param stationId   the station this container belongs to
 * @param parentId    optional parent container id, or {@code null} for a root
 * @param internalId  scannable internal id from the per-station namespace shared with items, or {@code null}
 * @param name        free-text display name (e.g. "Storage A", "Drawer 3")
 * @param kindId      optional reference to an {@link InventoryContainerKind}, or {@code null}
 * @param description free-text description of what's stored here, may be empty
 * @param createdAt   creation timestamp
 * @param createdBy   id of the member who created the container, or {@code null} if unknown
 */
public record InventoryContainer(
        int id,
        int stationId,
        Integer parentId,
        String internalId,
        String name,
        Integer kindId,
        String description,
        Instant createdAt,
        Integer createdBy) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryContainer> map() {
        return row -> new InventoryContainer(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getObject("parent_id", Integer.class),
                row.getString("internal_id"),
                row.getString("name"),
                row.getObject("kind_id", Integer.class),
                row.getString("description"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }
}
