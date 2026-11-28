/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One thing in a station's cart, named by the lending station's own ids.
 *
 * @param id                the line
 * @param lendingStationUid the partner station the gear is borrowed from
 * @param inventoryId       the partner's inventory
 * @param artId             the partner's kind counted, or {@code null}
 * @param itemId            the partner's single piece, or {@code null} for a count
 * @param quantity          how many are wanted, 1 for a piece
 * @param inventoryName     the inventory's name as last shown
 * @param lineName          the kind's or piece's name as last shown, empty for the whole inventory
 * @param pictureKey        the photo last shown for the line, or {@code null}
 */
public record LendingCartLine(
        int id,
        UUID lendingStationUid,
        int inventoryId,
        @Nullable Integer artId,
        @Nullable Integer itemId,
        int quantity,
        String inventoryName,
        String lineName,
        @Nullable String pictureKey) {

    /** Whether the line names one piece rather than counting. */
    public boolean isPiece() {
        return itemId != null;
    }

    public static RowMapping<LendingCartLine> map() {
        return row -> new LendingCartLine(
                row.getInt("id"),
                row.get("lending_station_uid", StandardValueConverter.UUID_STRING),
                row.getInt("inventory_id"),
                row.getObject("art_id", Integer.class),
                row.getObject("item_id", Integer.class),
                row.getInt("quantity"),
                row.getString("inventory_name"),
                row.getString("line_name"),
                row.getString("picture_key"));
    }
}
