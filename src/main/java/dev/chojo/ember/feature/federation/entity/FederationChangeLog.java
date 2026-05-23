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
        int id, int stationId, String contentType, int contentId, String changeType, Instant changedAt) {

    public enum ContentType {
        KB,
        QUIZ,
        PROTOCOL
    }

    public enum ChangeType {
        CREATED,
        UPDATED,
        DELETED
    }

    public static RowMapping<FederationChangeLog> map() {
        return row -> new FederationChangeLog(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("content_type"),
                row.getInt("content_id"),
                row.getString("change_type"),
                row.get("changed_at", INSTANT_TIMESTAMP));
    }
}
