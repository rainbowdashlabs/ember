/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** What a quiet system line in a group says happened. */
public enum SystemKind {
    /** The author added the subject to the group. */
    ADDED,
    /** The author left the group. */
    LEFT,
    /** The author, the owner, removed the subject from the group. */
    REMOVED,
    /** The author renamed the group to the line's text, or took its name away when the text is empty. */
    RENAMED,
    /** The subject became the owner because the owner left. */
    OWNER_PASSED
}
