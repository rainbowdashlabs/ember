/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.board.entity.BoardTicketAddress;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;

/**
 * Published when a comment is written.
 *
 * @param ticketAddress where the ticket's page is, when the comment hangs under one, and
 *                      {@code null} for everything else: a ticket is the one thing here that its id
 *                      alone does not open
 */
public record CommentCreated(
        int stationId,
        CommentEntityType entityType,
        int entityId,
        String entityTitle,
        BoardTicketAddress ticketAddress,
        int commentId,
        Integer parentCommentId,
        Integer parentAuthorId,
        Integer authorMemberId,
        String authorName,
        String preview)
        implements DomainEvent {}
