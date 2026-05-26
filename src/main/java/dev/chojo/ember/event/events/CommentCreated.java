/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;

public record CommentCreated(
        int stationId,
        CommentEntityType entityType,
        int entityId,
        String entityTitle,
        int commentId,
        Integer parentCommentId,
        Integer parentAuthorId,
        int authorMemberId,
        String authorName,
        String preview)
        implements DomainEvent {}
