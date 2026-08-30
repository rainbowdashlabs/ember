/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One member's standing subscription, as the station sees it.
 *
 * <p>Deliberately without the token. It is the whole key to that person's calendar, and a page that
 * lists it is a page from which somebody else's appointments can be read. What a station needs to
 * know is who has a subscription and whether anything ever fetches it, and neither question needs
 * the secret.
 *
 * @param memberId             the member the subscription belongs to
 * @param createdAt            when they set it up
 * @param icalPolledAt         when a calendar last fetched it, or null where none ever has
 * @param notificationPolledAt when a reader last fetched the notifications, or null where none has
 */
public record FeedUse(int memberId, Instant createdAt, Instant icalPolledAt, Instant notificationPolledAt) {
    public static RowMapping<FeedUse> map() {
        return row -> new FeedUse(
                row.getInt("member_id"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("ical_polled_at", INSTANT_TIMESTAMP),
                row.get("notification_polled_at", INSTANT_TIMESTAMP));
    }
}
