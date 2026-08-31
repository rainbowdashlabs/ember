/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.util.UUID;

/**
 * One item found by a tag, in the sparse shape every list of tag hits uses: the station's own list,
 * the list gathered from partners on this instance, and the list a partner on another instance
 * serves. The station is named by its stable identity rather than its local row, because the same
 * record travels across instances where a local row number means nothing.
 *
 * @param itemId        the item identifier at the station holding it
 * @param internalId    the identifier written on the piece, or {@code null} when it carries none
 * @param name          the name of the piece
 * @param inventoryId   the inventory the piece is filed under
 * @param inventoryName the name of that inventory
 * @param artId         the kind the piece is, or {@code null} when nobody has said
 * @param stationUid    the stable identity of the station holding it
 * @param stationName   the name of that station
 * @param tagName       the tag as the holding station spelled it
 * @param available     whether nobody holds the piece and it is not recorded as lost
 */
public record TaggedItemSummary(
        int itemId,
        String internalId,
        String name,
        int inventoryId,
        String inventoryName,
        Integer artId,
        UUID stationUid,
        String stationName,
        String tagName,
        boolean available) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<TaggedItemSummary> map() {
        return row -> new TaggedItemSummary(
                row.getInt("item_id"),
                row.getString("internal_id"),
                row.getString("item_name"),
                row.getInt("inventory_id"),
                row.getString("inventory_name"),
                (Integer) row.getObject("art_id"),
                row.get("station_uid", StandardValueConverter.UUID_STRING),
                row.getString("station_name"),
                row.getString("tag_name"),
                row.getBoolean("available"));
    }
}
