/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import org.jspecify.annotations.Nullable;

/**
 * Published when a comment is written.
 *
 * @param stationId       the station owning what the comment hangs under
 * @param entityType      what kind of thing the comment hangs under
 * @param entityTitle     what that thing is called
 * @param link            where a notification about the comment opens, as its target decides
 * @param commentId       the comment
 * @param parentCommentId the comment it answers, {@code null} for a top-level comment
 * @param parentAuthorId  the author of the answered comment as a member here, {@code null} when
 *                        there is nobody to tell
 * @param authorMemberId  the author as a member here, {@code null} when they are not one
 * @param authorName      the name the notification shows as the author
 * @param preview         the excerpt of the comment the notification shows
 * @param alsoTold        who is told about every comment on the target besides, {@code null} for
 *                        nobody; the author is left out of them
 */
public record CommentCreated(
        int stationId,
        CommentEntityType entityType,
        String entityTitle,
        NotificationLink link,
        int commentId,
        @Nullable Integer parentCommentId,
        @Nullable Integer parentAuthorId,
        @Nullable Integer authorMemberId,
        String authorName,
        String preview,
        @Nullable StationAudience alsoTold)
        implements DomainEvent {}
