/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;
import java.time.LocalDate;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an inventory lending request between two stations.
 */
public record LendingRequest(
        int id,
        int requestingStationId,
        int owningStationId,
        LendingStatus status,
        LocalDate requestedDateFrom,
        LocalDate requestedDateTo,
        int createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<LendingRequest> map() {
        return row -> new LendingRequest(
                row.getInt("id"),
                row.getInt("requesting_station_id"),
                row.getInt("owning_station_id"),
                LendingStatus.valueOf(row.getString("status")),
                row.getObject("requested_date_from", LocalDate.class),
                row.getObject("requested_date_to", LocalDate.class),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
