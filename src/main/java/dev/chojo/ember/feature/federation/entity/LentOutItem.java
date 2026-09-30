/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * A line of a lending request that is currently lent out (APPROVED or LENT status) from one
 * inventory of the owning station.
 */
public record LentOutItem(
        int requestItemId,
        int requestId,
        Integer itemId,
        int quantity,
        Integer assignedItemId,
        String status,
        LocalDate dateFrom,
        LocalDate dateTo,
        String requestingStationName) {
    public static RowMapping<LentOutItem> map() {
        return row -> new LentOutItem(
                row.getInt("request_item_id"),
                row.getInt("request_id"),
                row.getObject("item_id", Integer.class),
                row.getInt("quantity"),
                row.getObject("assigned_item_id", Integer.class),
                row.getString("status"),
                row.getObject("date_from", LocalDate.class),
                row.getObject("date_to", LocalDate.class),
                row.getString("requesting_station_name"));
    }
}
