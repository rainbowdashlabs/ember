/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.messages.entity;

/** What a quiet system line in a group or a station chat says happened. */
public enum SystemKind {
    /** The author added the subject to the group. */
    ADDED,
    /** The author left the group. */
    LEFT,
    /** The author, the owner, removed the subject from the group. */
    REMOVED,
    /** The author renamed the group or station chat to the line's text, or took a group's name away when the text is empty. */
    RENAMED,
    /** The subject became the owner because the owner left. */
    OWNER_PASSED,
    /** The author opened the station chat under the line's text as its name. */
    OPENED,
    /** The author closed the station chat, or nobody did where its group or tag was deleted. */
    CLOSED,
    /** The author opened the closed station chat again. */
    REOPENED
}
