/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record WaitlistVerificationToken(
        int id,
        String token,
        int listId,
        String firstname,
        String lastname,
        String email,
        String guardians,
        String fieldValues,
        String notes,
        Instant createdAt,
        Instant expiresAt) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public static RowMapping<WaitlistVerificationToken> map() {
        return row -> new WaitlistVerificationToken(
                row.getInt("id"),
                row.getString("token"),
                row.getInt("list_id"),
                row.getString("firstname"),
                row.getString("lastname"),
                row.getString("email"),
                row.getString("guardians"),
                row.getString("field_values"),
                row.getString("notes"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }
}
