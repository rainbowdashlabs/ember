/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Tracks content changes for federation sync polling.
 */
public record FederationChangeLog(
        int id, int stationId, ContentType contentType, int contentId, ChangeType changeType, Instant changedAt) {

    public static RowMapping<FederationChangeLog> map() {
        return row -> new FederationChangeLog(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getEnum("content_type", ContentType.class),
                row.getInt("content_id"),
                row.getEnum("change_type", ChangeType.class),
                row.get("changed_at", INSTANT_TIMESTAMP));
    }
}
