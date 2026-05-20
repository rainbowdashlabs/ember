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
 * @param token      the bearer token used to authenticate requests
 * @param expiresAt  when the session expires
 * @param createdAt  when the session was created
 * @param userAgent  the user agent string from the client that created the session
 * @param lastUsedAt when the session was last used
 * @param location   the client's location (e.g. country code from CF-IPCountry)
 */
public record AccountSession(
        int id,
        int accountId,
        String token,
        Instant expiresAt,
        Instant createdAt,
        String userAgent,
        Instant lastUsedAt,
        String location) {

    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<AccountSession> map() {
        return row -> new AccountSession(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("token"),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.getString("user_agent"),
                row.get("last_used_at", INSTANT_TIMESTAMP),
                row.getString("location"));
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
