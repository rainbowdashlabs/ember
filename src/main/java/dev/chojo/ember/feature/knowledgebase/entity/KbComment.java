/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;

import java.time.Instant;

/**
 * Represents a threaded comment on a knowledge base file.
 *
 * @param id        unique identifier of the comment
 * @param fileId    the KB file this comment belongs to
 * @param parentId  parent comment ID for threaded replies, or {@code null} for top-level comments
 * @param author    the identity of the comment author (may be null)
 * @param content   text content of the comment
 * @param deleted   whether the comment has been soft-deleted
 * @param createdAt timestamp when the comment was created
 * @param updatedAt timestamp when the comment was last updated, or {@code null}
 */
public record KbComment(
        int id,
        int fileId,
        Integer parentId,
        MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt) {
    /**
     * The knowledge base view of a stored comment on a file.
     *
     * @param comment a comment whose target is a knowledge base file
     * @return the same comment as a knowledge base comment
     */
    public static KbComment of(Comment comment) {
        return new KbComment(
                comment.id(),
                comment.targetId(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                comment.updatedAt());
    }
}
