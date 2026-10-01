/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.entity.CommentOrigin;
import dev.chojo.ember.feature.comment.entity.CreatedAudience;
import dev.chojo.ember.feature.comment.entity.Moderation;
import dev.chojo.ember.feature.comment.entity.TargetInfo;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;

import java.util.Optional;

/**
 * What a comment hangs under: one implementation per kind, living with its feature and bound by
 * kind in the map binder in {@code EmberModule}, so the comment system never imports a feature.
 *
 * <p>Everything the kinds share (storage, threads, the soft delete, previews, mentions and the
 * events) is {@link CommentService}'s; a target answers only what differs between them.
 */
public interface CommentTarget {

    /**
     * The kind of target this is.
     *
     * @return the kind
     */
    CommentEntityType type();

    /**
     * Station, title and address of the target.
     *
     * @param targetId the target
     * @return the target, empty when it does not exist
     */
    Optional<TargetInfo> find(int targetId);

    /**
     * The refusal answered for a target that does not exist, which each kind names on its own.
     *
     * @return the refusal
     */
    Refusal missing();

    /**
     * Refuses unless the member may read the comments on this target.
     *
     * @param session the reader
     * @param target  the target
     */
    void requireReadable(UserSession session, TargetInfo target);

    /**
     * Refuses unless the member may write a comment here.
     *
     * @param session the writer
     * @param target  the target
     */
    void requireWritable(UserSession session, TargetInfo target);

    /**
     * Whether the member may edit or delete a comment somebody else wrote.
     *
     * @param session the member
     * @param target  the target the comment hangs under
     * @param action  what they want to do with it
     * @return {@code true} when they may
     */
    boolean mayModerate(UserSession session, TargetInfo target, Moderation action);

    /**
     * Who a new comment on this target tells.
     *
     * @param target the target
     * @param origin where the comment was written from
     * @return the audience
     */
    CreatedAudience audienceFor(TargetInfo target, CommentOrigin origin);

    /**
     * Where a notification about a comment on this target opens: the target's page, landing on
     * the comment.
     *
     * @param target    the target
     * @param commentId the comment
     * @return the link
     */
    NotificationLink link(TargetInfo target, int commentId);
}
