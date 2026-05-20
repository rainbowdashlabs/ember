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
 * Represents a request to exchange an inventory item, typically for a different size.
 *
 * @param id              the unique exchange request identifier
 * @param stationId       the station this exchange belongs to
 * @param memberId        the member requesting the exchange
 * @param itemId          the current item being exchanged, or {@code null} if not item-specific
 * @param inventoryId     the inventory the exchange is for
 * @param oldSizeId       the current size of the item, or {@code null} if not applicable
 * @param newSizeId       the desired new size, or {@code null} if not applicable
 * @param exchangedItemId the replacement item assigned after exchange, or {@code null} if not yet exchanged
 * @param status          the current status of the exchange request
 * @param reason          the reason for the exchange
 * @param createdAt       when the request was created
 * @param updatedAt       when the request was last updated
 * @param createdBy       the member who created the request on behalf of another, or {@code null} if self-created
 */
public record ExchangeRequest(
        int id,
        int stationId,
        int memberId,
        Integer itemId,
        int inventoryId,
        Integer oldSizeId,
        Integer newSizeId,
        Integer exchangedItemId,
        ExchangeStatus status,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        Integer createdBy) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<ExchangeRequest> map() {
        return row -> new ExchangeRequest(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getInt("member_id"),
                row.getObject("item_id", Integer.class),
                row.getInt("inventory_id"),
                row.getObject("old_size_id", Integer.class),
                row.getObject("new_size_id", Integer.class),
                row.getObject("exchanged_item_id", Integer.class),
                row.getEnum("status", ExchangeStatus.class),
                row.getString("reason"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getObject("created_by", Integer.class));
    }
}
