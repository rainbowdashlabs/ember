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
 * A named set of things that belong together, kept so nobody has to remember it twice.
 *
 * <p>It is a template and holds no stock: what it says is copied wherever it is used, so editing it
 * changes nothing that was already asked for.
 *
 * @param stationId the station it belongs to
 * @param name      what the station calls it
 * @param note      free text about its purpose, possibly empty
 * @param createdBy the member who created it, or {@code null} once that member is gone
 * @param createdAt when it was created
 */
public record InventoryCollection(
        int id, int stationId, String name, String note, Integer createdBy, Instant createdAt) {

    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return the mapping
     */
    public static RowMapping<InventoryCollection> map() {
        return row -> new InventoryCollection(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("note"),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
