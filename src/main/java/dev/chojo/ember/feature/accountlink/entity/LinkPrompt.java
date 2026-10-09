/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.accountlink.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.ClusterUserType;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A link request as the person it asks is shown it: who asks, for what, and how the asker came to ask.
 * A station asks to link the account to one of its members; an association asks it to take a role.
 *
 * @param uid             the request, which accepting and declining name
 * @param stationName     the station that asks, or null where an association asks
 * @param memberName      the member the account would be linked to, as the station knows them, or null
 *                        where an association asks
 * @param associationName the association that asks, or null where a station asks
 * @param role            the role the association offers, or null where a station asks
 * @param origin          how the asker came to ask
 * @param invitedBy       who invited the address, or null for an import, an association and where that
 *                        member is gone
 * @param createdAt       when the asker first asked
 * @param expiresAt       until when the person may answer
 */
public record LinkPrompt(
        UUID uid,
        @Nullable String stationName,
        @Nullable String memberName,
        @Nullable String associationName,
        @Nullable ClusterUserType role,
        LinkOrigin origin,
        @Nullable String invitedBy,
        Instant createdAt,
        Instant expiresAt) {

    /**
     * Creates a row mapping for database result set conversion. Reads every column, so a query for one
     * kind of asker selects the other kind's columns as NULL.
     */
    public static RowMapping<LinkPrompt> map() {
        return row -> new LinkPrompt(
                row.get("uid", StandardValueConverter.UUID_STRING),
                row.getString("station_name"),
                row.getString("member_name"),
                row.getString("association_name"),
                row.getString("user_type") == null ? null : row.getEnum("user_type", ClusterUserType.class),
                row.getEnum("origin", LinkOrigin.class),
                row.getString("invited_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("expires_at", INSTANT_TIMESTAMP));
    }
}
