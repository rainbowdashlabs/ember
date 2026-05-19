/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record AccountSession(
        int id,
        int accountId,
        String token,
        Instant expiresAt,
        Instant createdAt,
        String userAgent,
        Instant lastUsedAt,
        String location) {

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

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
