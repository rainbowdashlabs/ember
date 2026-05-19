/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record StationApplication(
        int id,
        String firstName,
        String lastName,
        String email,
        String stationName,
        String introduction,
        String verificationToken,
        String status,
        String denyReason,
        Instant createdAt,
        Instant resolvedAt) {
    public static RowMapping<StationApplication> map() {
        return row -> new StationApplication(
                row.getInt("id"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getString("email"),
                row.getString("station_name"),
                row.getString("introduction"),
                row.getString("verification_token"),
                row.getString("status"),
                row.getString("deny_reason"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("resolved_at", INSTANT_TIMESTAMP));
    }
}
