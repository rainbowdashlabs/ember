/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.LocalDate;

/**
 * A borrowed piece as the borrowing station reads it: the row itself, whose gear it is, and when
 * it goes back.
 *
 * @param item             the borrowed row
 * @param ownerStationId   the partner station that owns it
 * @param ownerStationName the partner's name as it stands now, which is the one thing here that
 *                         is not part of the snapshot
 * @param loanRequestId    the lending request it came in on
 * @param dueOn            the day the loan was asked to run to, or {@code null} when none was named
 */
public record BorrowedItem(
        InventoryItem item, int ownerStationId, String ownerStationName, int loanRequestId, LocalDate dueOn) {
    public static RowMapping<BorrowedItem> map() {
        return row -> new BorrowedItem(
                InventoryItem.map().map(row),
                row.getInt("owner_station_id"),
                row.getString("owner_station_name"),
                row.getInt("loan_request_id"),
                row.getObject("due_on", LocalDate.class));
    }
}
