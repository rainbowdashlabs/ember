/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A tag a station puts on its items. Unlike an inventory it says nothing about where a thing is
 * kept, and unlike a size it says nothing about which piece it is: it is a standing property of the
 * piece, true whatever the occasion, and it spans inventories.
 *
 * @param id            the tag identifier
 * @param stationId     the station the tag belongs to
 * @param name          the tag as the station spelled it, which is what every list shows
 * @param canonicalName the trimmed lowercase name the database maintains, on which tags of the same
 *                      name match across stations
 * @param color         optional hex colour for the badge, {@code null} for the neutral one
 * @param position      where the tag sits in the station's own list
 */
public record InventoryTag(int id, int stationId, String name, String canonicalName, String color, int position) {

    /**
     * The form two spellings of one tag are compared in, which is what the database stores in
     * {@link #canonicalName()}.
     *
     * @param name a tag name as somebody typed it
     * @return the trimmed lowercase form, or an empty string when nothing was typed
     */
    public static String canonical(String name) {
        return name == null ? "" : name.strip().toLowerCase();
    }

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<InventoryTag> map() {
        return row -> new InventoryTag(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("canonical_name"),
                row.getString("color"),
                row.getInt("position"));
    }
}
