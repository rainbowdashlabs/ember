/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.entity;

/**
 * What a member does to a comment somebody else wrote.
 */
public enum Moderation {
    /** Rewrites the text. */
    EDIT,
    /** Removes the comment. */
    DELETE
}
