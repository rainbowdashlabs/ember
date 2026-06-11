/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a news article published within a station.
 *
 * @param id              unique identifier of the news entry
 * @param stationId       the station this news belongs to
 * @param title           headline of the news article
 * @param contentMarkdown article body in Markdown format
 * @param contentHtml     article body rendered as HTML
 * @param author          identity of the author (station UID + member UID), or {@code null} for deleted authors
 * @param publishedAt     timestamp when the article was published, or {@code null} if unpublished
 * @param createdAt       timestamp when the article was created
 */
public record News(
        int id,
        int stationId,
        String title,
        String contentMarkdown,
        String contentHtml,
        MemberIdentity author,
        Instant publishedAt,
        Instant createdAt,
        RestrictionMode restrictionMode,
        boolean restricted,
        boolean publicBlog) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<News> map() {
        return row -> {
            UUID authorStationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
            UUID authorMemberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity author = (authorStationUid != null && authorMemberUid != null)
                    ? new MemberIdentity(authorStationUid, authorMemberUid)
                    : null;

            return new News(
                    row.getInt("id"),
                    row.getInt("station_id"),
                    row.getString("title"),
                    row.getString("content_markdown"),
                    row.getString("content_html"),
                    author,
                    row.get("published_at", INSTANT_TIMESTAMP),
                    row.get("created_at", INSTANT_TIMESTAMP),
                    RestrictionMode.valueOf(row.getString("restriction_mode")),
                    row.getBoolean("restricted"),
                    row.getBoolean("public_blog"));
        };
    }
}
