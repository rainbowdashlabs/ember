/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TwoFactorFactor(
        int id,
        int accountId,
        TwoFactorKind kind,
        String label,
        Instant createdAt,
        Instant lastUsedAt,
        Instant disabledAt) {

    public static RowMapping<TwoFactorFactor> map() {
        return row -> new TwoFactorFactor(
                row.getInt("id"),
                row.getInt("account_id"),
                TwoFactorKind.valueOf(row.getString("kind")),
                row.getString("label"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("last_used_at", INSTANT_TIMESTAMP),
                row.get("disabled_at", INSTANT_TIMESTAMP));
    }

    public boolean isActive() {
        return disabledAt == null;
    }
}
