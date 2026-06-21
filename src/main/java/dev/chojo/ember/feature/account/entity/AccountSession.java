/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents an active login session for an account.
 *
 * @param id         the unique session identifier
 * @param accountId  the associated account identifier
 * @param tokenHash  the HMAC-SHA-256 hash of the bearer token; the raw bearer is never persisted
 * @param expiresAt  when the session expires
 * @param createdAt  when the session was created
 * @param userAgent  the user agent string from the client that created the session
 * @param lastUsedAt           when the session was last used
 * @param location             the client's location (e.g. country code from CF-IPCountry)
 * @param twoFactorVerifiedAt  when 2FA was last verified on this session (null if never)
 */
public record AccountSession(
        int id,
        int accountId,
        String tokenHash,
        Instant expiresAt,
        Instant createdAt,
        String userAgent,
        Instant lastUsedAt,
        String location,
        Instant twoFactorVerifiedAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountSession> map() {
        return row -> new AccountSession(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("token_hash"),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("user_agent"),
                row.get("last_used_at", INSTANT_TIMESTAMP),
                row.getString("location"),
                row.get("two_factor_verified_at", INSTANT_TIMESTAMP));
    }

    /**
     * Checks whether this session has expired based on the current time.
     *
     * @return {@code true} if the session has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
