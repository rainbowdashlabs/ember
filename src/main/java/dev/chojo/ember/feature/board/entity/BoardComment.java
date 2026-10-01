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

/**
 * A new comment on a ticket of a shared board, as the board's station answers the partner that
 * wrote it. Only the federation sends it: every other path speaks of a {@link Comment}.
 *
 * @param id        the comment
 * @param ticketId  the ticket it hangs under, on the serving station
 * @param parentId  the comment it answers, {@code null} for a top-level comment
 * @param author    who wrote it, {@code null} where nobody can be named
 * @param content   the text
 * @param deleted   whether it was removed
 * @param createdAt when it was written
 * @param updatedAt when it was last changed, {@code null} if never
 */
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
     * The wire form of a stored comment on a ticket.
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
}
