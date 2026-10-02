/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One row of an account's security history.
 *
 * @param stationId the station whose administration caused the event, or {@code null} where the
 *                  account itself or an instance administrator did
 */
public record TwoFactorAuditEntry(
        int id,
        int accountId,
        @Nullable Integer actorId,
        TwoFactorEvent event,
        @Nullable TwoFactorKind factorKind,
        @Nullable String userAgent,
        @Nullable String country,
        @Nullable Integer stationId,
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
                    row.getObject("station_id", Integer.class),
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }
}
