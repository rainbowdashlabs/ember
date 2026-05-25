/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Cached display name for a federated member.
 *
 * @param partnerId      the federation partner ID
 * @param remoteMemberId the member identifier from the partner station
 * @param displayName    the cached display name
 * @param cachedAt       when the name was last cached
 */
public record FederationMemberNameCache(int partnerId, String remoteMemberId, String displayName, Instant cachedAt) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<FederationMemberNameCache> map() {
        return row -> new FederationMemberNameCache(
                row.getInt("partner_id"),
                row.getString("remote_member_id"),
                row.getString("display_name"),
                row.get("cached_at", INSTANT_TIMESTAMP));
    }
}
