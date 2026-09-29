/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import java.time.Duration;

/**
 * What becomes of a mail a provider could not take for the moment.
 *
 * <p>A provider keeps a mail for the attempts it is given, each one further apart than the last, so a
 * relay having a bad minute is not hammered every round. Once it has used them the next provider of
 * the chain takes over straight away, because a different route is worth trying at once.
 *
 * <p>The last provider of the chain keeps trying beyond its attempts until the mail has been queued
 * for at least {@link #MIN_RETRY_WINDOW}. A relay that is down for a few minutes must not cost a
 * password reset, and with the default of two attempts on a single provider the attempts alone
 * would be spent within a minute. Only after that window is the mail given up on, so it shows as
 * failed to an operator instead of going out again and again for as long as the process runs.
 */
public final class MailRetryPolicy {

    /** The wait after the first failed attempt on a provider. */
    public static final Duration FIRST_DELAY = Duration.ofSeconds(30);

    /** The longest the queue ever holds a mail back between two attempts. */
    public static final Duration MAX_DELAY = Duration.ofHours(1);

    /** How long after it was queued a mail is still retried once the chain has nothing new to offer. */
    public static final Duration MIN_RETRY_WINDOW = Duration.ofHours(1);

    private MailRetryPolicy() {}

    /**
     * Where a mail goes after a transient failure.
     */
    public enum Step {
        /** The same provider tries again once the delay has passed. */
        RETRY_SAME_PROVIDER,
        /** The next provider of the chain takes the mail at once. */
        NEXT_PROVIDER,
        /** Every provider of the chain has used its attempts and the retry window is over; the mail has failed. */
        GIVE_UP
    }

    /**
     * Decides what follows a transient failure.
     *
     * @param attemptsUsed how many attempts the provider in turn has used, the failed one included
     * @param allowed      how many attempts that provider is given
     * @param position     which provider of the chain is in turn, counted from zero
     * @param chainSize    how many providers the chain holds
     * @param queuedFor    how long ago the mail was queued
     */
    public static Step after(int attemptsUsed, int allowed, int position, int chainSize, Duration queuedFor) {
        if (attemptsUsed < allowed) return Step.RETRY_SAME_PROVIDER;
        if (position + 1 < chainSize) return Step.NEXT_PROVIDER;
        if (queuedFor.compareTo(MIN_RETRY_WINDOW) < 0) return Step.RETRY_SAME_PROVIDER;
        return Step.GIVE_UP;
    }

    /**
     * How long the queue holds a mail back after its provider failed on it, doubling with every
     * attempt used and never beyond {@link #MAX_DELAY}.
     *
     * @param attemptsUsed how many attempts the provider in turn has used, the failed one included
     */
    public static Duration delayAfter(int attemptsUsed) {
        int doublings = Math.clamp(attemptsUsed - 1, 0, 30);
        Duration delay = FIRST_DELAY.multipliedBy(1L << doublings);
        return delay.compareTo(MAX_DELAY) > 0 ? MAX_DELAY : delay;
    }
}
