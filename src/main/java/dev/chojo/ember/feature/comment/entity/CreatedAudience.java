/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import dev.chojo.ember.feature.notifications.entity.StationAudience;
import org.jspecify.annotations.Nullable;

/**
 * Who a new comment notifies, beyond the comment itself.
 *
 * @param parentAuthor whether a reply notifies the author of the comment it answers
 * @param mentions     whether the members and audiences the comment mentions are notified
 * @param others       who is notified of every comment on the target besides, {@code null} for
 *                     nobody; the author is always left out of them
 * @param newThreads   who runs the target and is notified of every new top-level comment, which is
 *                     mostly a question to them, {@code null} for nobody; the author and whoever the
 *                     comment mentions by name are left out of them
 */
public record CreatedAudience(
        boolean parentAuthor,
        boolean mentions,
        @Nullable StationAudience others,
        @Nullable StationAudience newThreads) {

    /** An audience that notifies nobody about new threads in particular. */
    public CreatedAudience(boolean parentAuthor, boolean mentions, @Nullable StationAudience others) {
        this(parentAuthor, mentions, others, null);
    }

    /** Notifies nobody at all, the way a comment that arrives from a partner station does on most targets. */
    public static final CreatedAudience NOBODY = new CreatedAudience(false, false, null);

    /** Notifies the author of the answered comment and everybody mentioned, and nobody else. */
    public static final CreatedAudience THREAD = new CreatedAudience(true, true, null);

    /**
     * This audience, also notifying the given people about every new top-level comment.
     *
     * @param runners who runs the target
     */
    public CreatedAudience withNewThreadsTo(StationAudience runners) {
        return new CreatedAudience(parentAuthor, mentions, others, runners);
    }
}
