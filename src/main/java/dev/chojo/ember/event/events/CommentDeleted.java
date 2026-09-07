/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;

/**
 * Published when a comment is removed, whether it left a placeholder behind or vanished outright.
 *
 * @param stationId  the station the comment was written in
 * @param entityType what the comment hung under, which decides the page its notifications name
 * @param commentId  the removed comment
 */
public record CommentDeleted(int stationId, CommentEntityType entityType, int commentId) implements DomainEvent {}
