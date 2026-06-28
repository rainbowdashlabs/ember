/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an inventory lending request between two stations. The peer columns
 * ({@code requestingStationUid}, {@code owningStationUid}) carry the stations' stable UUIDs
 * rather than local integer ids, so a request still resolves correctly after one or both
 * stations have moved to a different Ember instance. A station is resolved local by
 * {@code SELECT 1 FROM station WHERE uid = ?}; if no row matches, the station lives on a remote
 * instance reached via {@code federation_partner}.
 */
public record LendingRequest(
        int id,
        UUID requestingStationUid,
        UUID owningStationUid,
        LendingStatus status,
        LocalDate requestedDateFrom,
        LocalDate requestedDateTo,
        Integer createdBy,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<LendingRequest> map() {
        return row -> new LendingRequest(
                row.getInt("id"),
                row.get("requesting_station_uid", StandardValueConverter.UUID_STRING),
                row.get("owning_station_uid", StandardValueConverter.UUID_STRING),
                row.getEnum("status", LendingStatus.class),
                row.getObject("requested_date_from", LocalDate.class),
                row.getObject("requested_date_to", LocalDate.class),
                row.getObject("created_by", Integer.class),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
