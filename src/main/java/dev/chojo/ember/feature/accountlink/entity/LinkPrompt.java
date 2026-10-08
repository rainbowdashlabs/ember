/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A link request as the person it asks is shown it: who asks, for which member, and how the station
 * came to ask.
 *
 * @param uid         the request, which accepting and declining name
 * @param stationName the station that asks
 * @param memberName  the member the account would be linked to, as the station knows them
 * @param origin      how the station came to ask
 * @param invitedBy   who invited the address, or null for an import and where that member is gone
 * @param createdAt   when the station first asked
 * @param expiresAt   until when the person may answer
 */
public record LinkPrompt(
        UUID uid,
        String stationName,
        String memberName,
        LinkOrigin origin,
        @Nullable String invitedBy,
        Instant createdAt,
        Instant expiresAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<LinkPrompt> map() {
        return row -> new LinkPrompt(
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getString("station_name"),
                row.getString("member_name"),
                row.getEnum("origin", LinkOrigin.class),
                row.getString("invited_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }
}
