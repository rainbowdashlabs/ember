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
 * Represents a one-time-use token associated with an account for operations like email verification or password reset.
 *
 * @param id        the unique token identifier
 * @param accountId the associated account identifier
 * @param tokenHash the HMAC-SHA-256 hash of the issued token; the raw token is never persisted
 * @param tokenType the type of operation this token is for
 * @param metadata  optional metadata (e.g. new email address for email change tokens, station ID for deletion)
 * @param expiresAt when the token expires
 * @param createdAt when the token was created
 */
public record AccountToken(
        int id,
        int accountId,
        String tokenHash,
        TokenType tokenType,
        String metadata,
        Instant expiresAt,
        Instant createdAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<AccountToken> map() {
        return row -> new AccountToken(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("token_hash"),
                row.getEnum("token_type", TokenType.class),
                row.getString("metadata"),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    /**
     * Checks whether this token has expired based on the current time.
     *
     * @return {@code true} if the token has expired
     */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
