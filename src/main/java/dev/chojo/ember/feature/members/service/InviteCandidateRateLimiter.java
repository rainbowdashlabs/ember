/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Bounds how often one account searches for people to invite by name.
 *
 * <p>The search answers as the manager types, so a burst is normal; going through the alphabet to list
 * who has an account here is not. One bucket per account, which a manager of several stations shares
 * between them.
 *
 * <p>A development instance keeps the machinery with a capacity nothing reaches, so the end-to-end
 * suite never waits.
 */
@Singleton
public class InviteCandidateRateLimiter {

    /** Searches one account may make at once before it has to wait. */
    public static final int CAPACITY = 30;

    /** How often one more search becomes available once the bucket ran dry. */
    public static final Duration REFILL = Duration.ofSeconds(10);

    private static final int DEV_CAPACITY = 1_000_000;
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket perAccount;

    @Inject
    public InviteCandidateRateLimiter(Demo demoConfig) {
        this(Clock.systemUTC(), demoConfig.dev() ? DEV_CAPACITY : CAPACITY);
    }

    /**
     * Builds the limiter with the production capacity on the given clock.
     *
     * @param clock the clock the bucket refills by
     */
    public InviteCandidateRateLimiter(Clock clock) {
        this(clock, CAPACITY);
    }

    private InviteCandidateRateLimiter(Clock clock, int capacity) {
        this.perAccount = new LeakyBucket(capacity, REFILL, PRUNE_AFTER, clock);
    }

    /**
     * Counts a search by the given account.
     *
     * @param accountId the searching account
     * @return empty when it is admitted, or the seconds until the next one would be
     */
    public Optional<Long> trySearch(int accountId) {
        return perAccount.tryAcquire("account:" + accountId);
    }
}
