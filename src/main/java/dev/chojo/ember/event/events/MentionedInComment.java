/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import org.jspecify.annotations.Nullable;

/**
 * Published when a member is @mentioned in a comment.
 *
 * @param stationId         the station where the comment was posted
 * @param mentionedMemberId the member ID of the mentioned user
 * @param authorMemberId    the member ID of the comment author, {@code null} when they are not a member of the station
 * @param authorName        the display name of the comment author
 * @param entityType        the type of entity the comment is on (e.g. "event")
 * @param entityTitle       the title/name of the entity
 * @param link              where the notification opens: the entity's page, landing on the comment
 * @param commentId         the comment carrying the mention
 * @param preview           a short snippet of the comment text (truncated by the publisher) so
 *                          the feed entry can surface the surrounding context without a lookup
 */
public record MentionedInComment(
        int stationId,
        int mentionedMemberId,
        @Nullable Integer authorMemberId,
        String authorName,
        CommentEntityType entityType,
        String entityTitle,
        NotificationLink link,
        int commentId,
        String preview)
        implements DomainEvent {}
