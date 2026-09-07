/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A device with no passkey asking to be let in: the new device holds the poll secret, an old,
 * signed-in one confirms the code, and the poll that follows hands the new device a token that
 * may create exactly one credential. The hashes never leave the database; what travels is only
 * ever the raw value each side already holds.
 *
 * @param approvedAccountId the account whose session approved the request, or {@code null}
 * @param enrollTokenIssued whether the one-time enrolment token was already handed out; it is
 *         delivered exactly once, on the first poll after the approval
 */
public record PasskeyDeviceRequest(
        int id,
        Integer approvedAccountId,
        Instant approvedAt,
        Instant consumedAt,
        Instant expiresAt,
        int attempts,
        String requestedUserAgent,
        String requestedCountry,
        boolean enrollTokenIssued,
        Instant createdAt) {

    public static RowMapping<PasskeyDeviceRequest> map() {
        return row -> new PasskeyDeviceRequest(
                row.getInt("id"),
                row.getObject("approved_account_id", Integer.class),
                row.get("approved_at", INSTANT_TIMESTAMP),
                row.get("consumed_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.getInt("attempts"),
                row.getString("requested_user_agent"),
                row.getString("requested_country"),
                row.getBoolean("enroll_token_issued"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public boolean isExpired() {
        return expiresAt.isBefore(Instant.now());
    }

    public boolean isApproved() {
        return approvedAt != null;
    }
}
