/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record TwoFactorAuditEntry(
        int id,
        int accountId,
        Integer actorId,
        TwoFactorEvent event,
        TwoFactorKind factorKind,
        String userAgent,
        String country,
        Instant createdAt) {

    public static RowMapping<TwoFactorAuditEntry> map() {
        return row -> {
            String kindStr = row.getString("factor_kind");
            String eventStr = row.getString("event");
            return new TwoFactorAuditEntry(
                    row.getInt("id"),
                    row.getInt("account_id"),
                    row.getObject("actor_id", Integer.class),
                    TwoFactorEvent.valueOf(eventStr),
                    kindStr != null ? TwoFactorKind.valueOf(kindStr) : null,
                    row.getString("user_agent"),
                    row.getString("country"),
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }
}
