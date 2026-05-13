/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record AccountToken(
        int id, int accountId, String token, TokenType tokenType, Instant expiresAt, Instant createdAt) {

    public static RowMapping<AccountToken> map() {
        return row -> new AccountToken(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("token"),
                row.getEnum("token_type", TokenType.class),
                row.get("expires_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
