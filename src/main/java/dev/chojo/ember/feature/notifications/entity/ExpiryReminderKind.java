/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Which sentence an expiry reminder is worded by.
 */
public enum ExpiryReminderKind {
    /** The date is still ahead, by at least a day. */
    EXPIRES_IN,
    /** Today is the last valid day. */
    EXPIRES_TODAY,
    /** The last valid day has passed. */
    EXPIRED,
    /** Member management's reminder, naming the members who became due. */
    MEMBERS_DUE;

    /**
     * The sentence for a member's own date.
     *
     * @param daysLeft the days until the last valid day, negative once it has passed
     */
    public static ExpiryReminderKind forDaysLeft(long daysLeft) {
        if (daysLeft > 0) return EXPIRES_IN;
        return daysLeft == 0 ? EXPIRES_TODAY : EXPIRED;
    }
}
