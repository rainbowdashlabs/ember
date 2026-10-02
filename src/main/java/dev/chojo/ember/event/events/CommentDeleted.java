/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;

/**
 * Published when a comment is removed, whether it left a placeholder behind or vanished outright.
 *
 * @param stationId  the station the comment was written in
 * @param entityType what the comment hung under
 * @param link       the link notifications about the comment carry, which is what their
 *                   withdrawal matches
 * @param commentId  the removed comment
 */
public record CommentDeleted(int stationId, CommentEntityType entityType, NotificationLink link, int commentId)
        implements DomainEvent {}
