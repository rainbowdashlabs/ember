/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FederationMetadataCache(
        int id, int partnerId, String contentType, int remoteId, String title, String description, Instant cachedAt) {

    public static RowMapping<FederationMetadataCache> map() {
        return row -> new FederationMetadataCache(
                row.getInt("id"),
                row.getInt("partner_id"),
                row.getString("content_type"),
                row.getInt("remote_id"),
                row.getString("title"),
                row.getString("description"),
                row.get("cached_at", INSTANT_TIMESTAMP));
    }
}
