/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

/**
 * Where a comment was written from, which decides whom it tells.
 */
public enum CommentOrigin {
    /** By a member of the station owning the target, or of another station on its own pages. */
    LOCAL,
    /** By a member of a partner station, arriving over the federation. */
    PARTNER
}
