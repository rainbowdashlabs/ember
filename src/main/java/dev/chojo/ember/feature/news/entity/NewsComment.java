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
 * Represents a comment on a news article, supporting threaded replies.
 *
 * @param id        unique identifier of the comment
 * @param newsId    the news article this comment belongs to
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param author    identity of the comment author (station UID + member UID), or {@code null} for deleted/system comments
 * @param content   text content of the comment
 * @param deleted   whether the comment has been soft-deleted
 * @param createdAt timestamp when the comment was created
 */
public record NewsComment(
        int id,
        int newsId,
        Integer parentId,
        MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<NewsComment> map() {
        return row -> {
            UUID authorStationUid = row.get("author_station_uid", StandardValueConverter.UUID_STRING);
            UUID authorMemberUid = row.get("author_member_uid", StandardValueConverter.UUID_STRING);
            MemberIdentity author = (authorStationUid != null && authorMemberUid != null)
                    ? new MemberIdentity(authorStationUid, authorMemberUid)
                    : null;

            return new NewsComment(
                    row.getInt("id"),
                    row.getInt("news_id"),
                    row.getObject("parent_id", Integer.class),
                    author,
                    row.getString("content"),
                    row.getBoolean("deleted"),
                    row.get("created_at", INSTANT_TIMESTAMP));
        };
    }
}
