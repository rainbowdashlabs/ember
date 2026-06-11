/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an application to create a new station.
 *
 * @param id                the unique application identifier
 * @param firstName         the applicant's first name
 * @param lastName          the applicant's last name
 * @param email             the applicant's email address
 * @param stationName       the desired station name
 * @param introduction      an optional introduction text from the applicant
 * @param verificationToken the email verification token, {@code null} once verified
 * @param status            the application status (unverified, pending, accepted, denied)
 * @param denyReason        the reason for denial, or {@code null} if not denied
 * @param createdAt         the timestamp when the application was created
 * @param resolvedAt        the timestamp when the application was accepted or denied, or {@code null}
 */
public record StationApplication(
        int id,
        String firstName,
        String lastName,
        String email,
        String stationName,
        String introduction,
        String verificationToken,
        ApplicationStatus status,
        String denyReason,
        Instant createdAt,
        Instant resolvedAt) {
    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return a {@link RowMapping} that maps database rows to {@link StationApplication} instances
     */
    public static RowMapping<StationApplication> map() {
        return row -> new StationApplication(
                row.getInt("id"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getString("email"),
                row.getString("station_name"),
                row.getString("introduction"),
                row.getString("verification_token"),
                row.getEnum("status", ApplicationStatus.class),
                row.getString("deny_reason"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("resolved_at", INSTANT_TIMESTAMP));
    }
}
