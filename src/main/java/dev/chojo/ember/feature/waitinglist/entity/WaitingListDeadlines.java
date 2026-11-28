/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.entity;

import java.time.Duration;

/**
 * The fixed spans of a waiting list that its mails print, kept in one place so the text and the
 * sweep that acts on it cannot drift apart.
 */
public final class WaitingListDeadlines {
    /** How long the link that confirms a public registration stays good for. */
    public static final Duration VERIFICATION_VALIDITY = Duration.ofHours(24);

    /** How long an entry asked to confirm its interest has before it is removed. */
    public static final Duration CONFIRMATION_GRACE = Duration.ofDays(30);

    /** How long before that removal the last warning goes out. */
    public static final Duration REMOVAL_WARNING_AHEAD = Duration.ofDays(14);

    private WaitingListDeadlines() {}

    /** The day after the reminder on which the last warning goes out. */
    public static long removalWarningDay() {
        return CONFIRMATION_GRACE.minus(REMOVAL_WARNING_AHEAD).toDays();
    }
}
