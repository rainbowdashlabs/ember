/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.news.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;

import java.time.Instant;

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
     * The news view of a stored comment on a news entry.
     *
     * @param comment a comment whose target is a news entry
     * @return the same comment as a news comment
     */
    public static NewsComment of(Comment comment) {
        return new NewsComment(
                comment.id(),
                comment.targetId(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt());
    }
}
