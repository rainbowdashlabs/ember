/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TrustedDevice(
        int id,
        int accountId,
        String tokenHash,
        String userAgent,
        Instant createdAt,
        Instant trustedUntil,
        Instant lastSeenAt,
        Instant revokedAt) {

    public static RowMapping<TrustedDevice> map() {
        return row -> new TrustedDevice(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("token_hash"),
                row.getString("user_agent"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("trusted_until", INSTANT_TIMESTAMP),
                row.get("last_seen_at", INSTANT_TIMESTAMP),
                row.get("revoked_at", INSTANT_TIMESTAMP));
    }

    public boolean isValid() {
        return revokedAt == null && Instant.now().isBefore(trustedUntil);
    }
}
