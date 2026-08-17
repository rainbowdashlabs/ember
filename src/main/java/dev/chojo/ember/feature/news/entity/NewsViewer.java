/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One row of the news-view-summary response - a member who either saw or has not yet seen a
 * specific news entry. {@code seenAt} is {@code null} for the unseen group, otherwise the
 * moment the news first became fully visible in the member's viewport.
 *
 * @param member the station member who either saw or has not yet seen the news
 * @param seenAt when the member first fully saw the news, or {@code null} if not yet seen
 */
public record NewsViewer(MemberIdentity member, Instant seenAt) {

    public static RowMapping<NewsViewer> map() {
        return row -> {
            UUID stationUid = row.get("station_uid", StandardValueConverter.UUID_STRING);
            UUID memberUid = row.get("member_uid", StandardValueConverter.UUID_STRING);
            // Column comes back null for the unseen branch (LEFT JOIN against news_view).
            Instant seenAt = row.getObject("seen_at") != null ? row.get("seen_at", INSTANT_TIMESTAMP) : null;
            return new NewsViewer(new MemberIdentity(stationUid, memberUid), seenAt);
        };
    }
}
