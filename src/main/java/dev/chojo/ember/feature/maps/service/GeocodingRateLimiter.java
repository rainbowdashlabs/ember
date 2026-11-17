/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Per-account rate limiter for address lookups, backed by a {@link LeakyBucket}.
 *
 * <p>Every lookup the cache does not answer is a request to the operator's address search, which
 * for the public Photon server falls under a fair-use policy. A person correcting an address presses
 * the button a few times in a row, so the bucket admits {@value #BURST_CAPACITY} lookups at once
 * and refills at {@value #REFILL_PER_MINUTE} per minute.
 *
 * <p>A development instance keeps the machinery with a capacity nothing reaches, the same as the
 * other limiters, so the end-to-end suite never trips it.
 */
@Singleton
public class GeocodingRateLimiter {

    /** Lookups admitted at once from an idle bucket. */
    public static final int BURST_CAPACITY = 10;

    /** Sustained refill. */
    public static final int REFILL_PER_MINUTE = 6;

    private static final int DEV_CAPACITY = 1_000_000;
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket bucket;

    @Inject
    public GeocodingRateLimiter(Demo demoConfig) {
        this(Clock.systemUTC(), demoConfig.dev() ? DEV_CAPACITY : BURST_CAPACITY);
    }

    /**
     * A limiter on a clock the caller drives, at the production capacity.
     *
     * @param clock the clock refills are measured on
     */
    public GeocodingRateLimiter(Clock clock) {
        this(clock, BURST_CAPACITY);
    }

    private GeocodingRateLimiter(Clock clock, int capacity) {
        this.bucket = new LeakyBucket(capacity, REFILL_PER_MINUTE, PRUNE_AFTER, clock);
    }

    /**
     * Attempts to take one lookup for the given account.
     *
     * @param accountId the account asking
     * @return empty when the lookup is allowed, or the seconds until the next refill
     */
    public Optional<Long> tryAcquire(int accountId) {
        return bucket.tryAcquire(Integer.toString(accountId));
    }
}
