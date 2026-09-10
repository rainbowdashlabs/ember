/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.entity;

/**
 * What becomes of a message once its attachments have been dealt with.
 *
 * <p>Deleting is deliberately not among them. Giving a background job the power to destroy mail in
 * somebody's mailbox, on a rule they may have mistyped, buys nothing that {@link #MOVE} does not,
 * and moving can be undone by whoever finds out.
 */
public enum MailRuleAction {
    /** Left exactly as it was, for a mailbox the station connects with a read-only account. */
    NOTHING,
    /** Marked as read, which is the default and enough for most mailboxes. */
    MARK_SEEN,
    /** Flagged, for a station that reads its mail itself and wants to see what was taken. */
    FLAG,
    /** Moved to another folder, which is what a station with a busy mailbox will want. */
    MOVE
}
