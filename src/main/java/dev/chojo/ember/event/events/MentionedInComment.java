/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;

/**
 * Published when a member is @mentioned in a comment.
 *
 * @param stationId       the station where the comment was posted
 * @param mentionedMemberId the member ID of the mentioned user
 * @param authorMemberId  the member ID of the comment author
 * @param authorName      the display name of the comment author
 * @param entityType      the type of entity the comment is on (e.g. "event")
 * @param entityId        the ID of the entity
 */
public record MentionedInComment(
        int stationId,
        int mentionedMemberId,
        int authorMemberId,
        String authorName,
        CommentEntityType entityType,
        int entityId)
        implements DomainEvent {}
