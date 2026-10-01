/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.MentionType;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import org.jspecify.annotations.Nullable;

/**
 * Published when a group or special target is @mentioned in a comment.
 * The handler resolves the mention type and target ID to individual members.
 *
 * @param stationId       the station where the comment was posted
 * @param authorMemberId  the member ID of the comment author, {@code null} when they are not a member of the station
 * @param authorName      the display name of the comment author
 * @param entityType      the type of entity the comment is on
 * @param entityTitle     the title/name of the entity
 * @param mentionType     the type of mention
 * @param mentionTargetId the ID of the target (group ID or event ID)
 * @param link            where the notification opens: the entity's page, landing on the comment
 * @param commentId       the comment carrying the mention
 * @param preview         short snippet of the comment text so the feed entry surfaces context
 */
public record BulkMentionedInComment(
        int stationId,
        @Nullable Integer authorMemberId,
        String authorName,
        CommentEntityType entityType,
        String entityTitle,
        MentionType mentionType,
        int mentionTargetId,
        NotificationLink link,
        int commentId,
        String preview)
        implements DomainEvent {}
