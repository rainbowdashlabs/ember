/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import dev.chojo.ember.feature.mailimport.entity.MailMailbox;

import java.time.Duration;
import java.time.Instant;

/**
 * When a mailbox is next due, and when it has failed often enough to stop being worth trying.
 *
 * <p>Kept apart from the thread that does the visiting so that both answers can be tested against an
 * injected moment. A scheduler that could only be observed by waiting for it is a scheduler whose
 * arithmetic nobody checks.
 */
public final class MailImportSchedule {

    /**
     * How many failures in a row take a mailbox out of the rotation.
     *
     * <p>Six is roughly a day and a half at the shortest interval, and the point is not to give up
     * quickly: it is to stop asking a provider that keeps refusing, before the provider decides the
     * account is the problem.
     */
    public static final int FAILURES_BEFORE_SUSPENSION = 6;

    private static final int LONGEST_BACKOFF_STEPS = 5;

    private MailImportSchedule() {}

    /**
     * Whether this mailbox should be visited now.
     *
     * <p>A mailbox that has never been checked is always due, which is what makes a newly added one
     * import without anybody waiting a quarter of an hour to see whether their rule works.
     *
     * @param mailbox   the mailbox
     * @param floor     the shortest interval the operator allows, whatever the station asked for
     * @param now       the moment being judged
     * @return whether it is due
     */
    public static boolean due(MailMailbox mailbox, int floor, Instant now) {
        if (!mailbox.enabled() || mailbox.suspended()) return false;
        if (mailbox.lastCheckAt() == null) return true;
        return !now.isBefore(mailbox.lastCheckAt().plus(waitFor(mailbox, floor)));
    }

    /**
     * How long to wait before visiting this mailbox again.
     *
     * <p>The station's interval, held to the operator's floor, and doubled for each consecutive failure
     * up to a ceiling. Backing off answers a mailbox that fails; it is not what keeps one broken mailbox
     * from stalling the others, which is what the connection and read timeouts are for.
     *
     * @param mailbox the mailbox
     * @param floor   the shortest interval the operator allows
     * @return how long until it is due again
     */
    public static Duration waitFor(MailMailbox mailbox, int floor) {
        int minutes = Math.max(mailbox.intervalMinutes(), floor);
        int steps = Math.min(mailbox.failureCount(), LONGEST_BACKOFF_STEPS);
        return Duration.ofMinutes(minutes * (1L << steps));
    }

    /**
     * Whether this many failures is enough to stop trying.
     *
     * @param failureCount how many have happened in a row, counting the one just recorded
     * @return whether the mailbox should be suspended
     */
    public static boolean shouldSuspend(int failureCount) {
        return failureCount >= FAILURES_BEFORE_SUSPENSION;
    }
}
