/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;

/**
 * Published when a group or special target is @mentioned in a comment.
 * The handler resolves the mention type and target ID to individual members.
 *
 * @param stationId       the station where the comment was posted
 * @param authorMemberId  the member ID of the comment author
 * @param authorName      the display name of the comment author
 * @param entityType      the type of entity the comment is on
 * @param entityId        the ID of the entity
 * @param entityTitle     the title/name of the entity
 * @param mentionType     the type of mention
 * @param mentionTargetId the ID of the target (group ID or event ID)
 * @param preview         short snippet of the comment text so the feed entry surfaces context
 */
public record BulkMentionedInComment(
        int stationId,
        Integer authorMemberId,
        String authorName,
        CommentEntityType entityType,
        int entityId,
        String entityTitle,
        MentionType mentionType,
        int mentionTargetId,
        String preview)
        implements DomainEvent {}
