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
 * Represents a comment on a news article, supporting threaded replies.
 *
 * @param id        unique identifier of the comment
 * @param newsId    the news article this comment belongs to
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param authorId  member ID of the comment author
 * @param content   text content of the comment
 * @param createdAt timestamp when the comment was created
 */
public record NewsComment(int id, int newsId, Integer parentId, int authorId, String content, Instant createdAt) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<NewsComment> map() {
        return row -> new NewsComment(
                row.getInt("id"),
                row.getInt("news_id"),
                row.getObject("parent_id", Integer.class),
                row.getInt("author_id"),
                row.getString("content"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
