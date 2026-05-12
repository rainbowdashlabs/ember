/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record NewsComment(int id, int newsId, Integer parentId, int authorId, String content, Instant createdAt) {
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
