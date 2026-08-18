/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed;

import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Per-token rate limiter for the personal feed endpoints, backed by a {@link LeakyBucket}.
 *
 * <p>The bucket gives readers {@link #BURST_CAPACITY} requests immediately available with a
 * sustained refill of {@link #REFILL_PER_MINUTE} per minute. That covers the realistic
 * shape of feed traffic - a reader opens or refreshes and fires a small burst, then idles
 * - while still shielding the backend from misbehaving aggregators.
 *
 * <p>The bucket is shared per token across {@code events.ics},
 * {@code notifications.rss}, and {@code notifications.atom} - all three hit the same
 * backing data, so the cap is "total feed reads per token". The token-scoped image
 * endpoint is intentionally exempt: a single feed body may link many images and they have
 * their own 24 h cache.
 *
 * <p>State is held in-memory. A restart resets every bucket; that's fine - the worst case
 * is a brief permissive window where pollers' first requests succeed.
 */
@Singleton
public class FeedRateLimiter {

    /**
     * Burst capacity - how many requests are admitted immediately from an idle bucket.
     */
    public static final int BURST_CAPACITY = 10;

    /**
     * Sustained refill rate.
     */
    public static final int REFILL_PER_MINUTE = 5;

    /**
     * Buckets idle past this duration are pruned out of the in-memory map.
     */
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket bucket;

    public FeedRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * Visible-for-testing constructor that lets tests drive time deterministically.
     */
    public FeedRateLimiter(Clock clock) {
        this.bucket = new LeakyBucket(BURST_CAPACITY, REFILL_PER_MINUTE, PRUNE_AFTER, clock);
    }

    /**
     * Attempts to consume one slot for {@code token}.
     *
     * @return empty when the request is allowed, or the seconds until the next refill when
     * the caller should be served {@code 429} with {@code Retry-After}
     */
    public Optional<Long> tryAcquire(String token) {
        return bucket.tryAcquire(token);
    }
}
