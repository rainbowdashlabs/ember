/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * Represents a news article published within a station.
 *
 * @param id               unique identifier of the news entry
 * @param stationId        the station this news belongs to
 * @param title            headline of the news article
 * @param contentMarkdown  article body in Markdown format
 * @param contentHtml      article body rendered as HTML
 * @param authorId         member ID of the author
 * @param publishedAt      timestamp when the article was published, or {@code null} if unpublished
 * @param createdAt        timestamp when the article was created
 */
public record News(
        int id,
        int stationId,
        String title,
        String contentMarkdown,
        String contentHtml,
        int authorId,
        Instant publishedAt,
        Instant createdAt) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<News> map() {
        return row -> new News(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("title"),
                row.getString("content_markdown"),
                row.getString("content_html"),
                row.getInt("author_id"),
                row.get("published_at", INSTANT_TIMESTAMP),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
