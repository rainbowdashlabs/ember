/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

public record BoardComment(
        int id,
        int ticketId,
        @Nullable Integer parentId,
        @Nullable MemberIdentity author,
        String content,
        boolean deleted,
        Instant createdAt,
        @Nullable Instant updatedAt) {

    /**
     * The board view of a stored comment on a ticket.
     *
     * @param comment a comment whose target is a board ticket
     * @return the same comment as a board comment
     */
    public static BoardComment of(Comment comment) {
        return new BoardComment(
                comment.id(),
                comment.targetId(),
                comment.parentId(),
                comment.author(),
                comment.content(),
                comment.deleted(),
                comment.createdAt(),
                comment.updatedAt());
    }

    public BoardComment withAuthor(MemberIdentity author) {
        return new BoardComment(id, ticketId, parentId, author, content, deleted, createdAt, updatedAt);
    }
}
