/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A borrowed piece as the borrowing station reads it: the row itself, whose gear it is, and when
 * it goes back.
 *
 * @param item             the borrowed row
 * @param ownerStationUid  the partner station that owns it, wherever it runs
 * @param ownerStationId   that station where it runs on this installation, or {@code null} where it
 *                         runs on another one
 * @param ownerStationName the partner's name as it stands now, which is the one thing here that
 *                         is not part of the snapshot
 * @param loanRequestId    the lending request it came in on
 * @param dueOn            the day the loan was asked to run to, or {@code null} when none was named
 */
public record BorrowedItem(
        InventoryItem item,
        UUID ownerStationUid,
        @Nullable Integer ownerStationId,
        String ownerStationName,
        int loanRequestId,
        @Nullable LocalDate dueOn) {
    public static RowMapping<BorrowedItem> map() {
        return row -> new BorrowedItem(
                InventoryItem.map().map(row),
                row.get("owner_station_uid", StandardValueConverter.UUID_STRING),
                row.getObject("owner_station_id", Integer.class),
                row.getString("owner_station_name"),
                row.getInt("loan_request_id"),
                row.getObject("due_on", LocalDate.class));
    }
}
