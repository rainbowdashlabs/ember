/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

import dev.chojo.ember.feature.notifications.entity.StationAudience;
import org.jspecify.annotations.Nullable;

/**
 * Who a new comment tells, beyond the comment itself.
 *
 * @param parentAuthor whether a reply tells the author of the comment it answers
 * @param mentions     whether the members and audiences the comment mentions are told
 * @param others       who is told about every comment on the target besides, {@code null} for
 *                     nobody; the author is always left out of them
 */
public record CreatedAudience(
        boolean parentAuthor, boolean mentions, @Nullable StationAudience others) {

    /** Tells nobody at all, the way a comment that arrives from a partner station does on most targets. */
    public static final CreatedAudience NOBODY = new CreatedAudience(false, false, null);

    /** Tells the author of the answered comment and everybody mentioned, and nobody else. */
    public static final CreatedAudience THREAD = new CreatedAudience(true, true, null);
}
