/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record FeedToken(
        int memberId, String token, Instant createdAt, Instant icalPolledAt, Instant notificationPolledAt) {
    public static RowMapping<FeedToken> map() {
        return row -> new FeedToken(
                row.getInt("member_id"),
                row.getString("token"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("ical_polled_at", INSTANT_TIMESTAMP),
                row.get("notification_polled_at", INSTANT_TIMESTAMP));
    }
}
