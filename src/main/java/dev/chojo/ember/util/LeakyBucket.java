/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reusable per-key leaky-bucket (a.k.a. token bucket) rate limiter.
 *
 * <p>Each key has its own bucket with a fixed {@code capacity}; tokens accrue continuously
 * at {@code refill rate} until the bucket is full. Every {@link #tryAcquire(String)}
 * consumes one token; when no token is available the caller is told how long to wait
 * before the next one arrives.
 *
 * <p>Bursting is the realistic shape of most user-facing traffic: a UI opens, fires several
 * back-to-back requests, then idles. A pure "N per minute" cap would reject the natural
 * burst even though the long-run rate is fine. Leaky bucket gives callers {@code capacity}
 * requests immediately available and {@code refillRate} replenished per unit time, so the
 * sustained rate is bounded but real-world bursts pass cleanly.
 *
 * <p>State is held in-memory in a {@link ConcurrentHashMap}; a restart resets every bucket.
 * Buckets idle past {@code pruneAfter} are dropped opportunistically so the map cannot
 * grow without bound when keys are short-lived (e.g. one-shot session tokens).
 *
 * <p>Thread-safe: the per-key compute happens under a {@code ConcurrentHashMap.compute()}
 * lock so concurrent callers on the same key cannot both pass through when only one slot
 * remains.
 *
 * @see dev.chojo.ember.feature.feed.FeedRateLimiter for a typical use site.
 */
public final class LeakyBucket {

    private final int capacity;
    private final Duration refillInterval;
    private final Duration pruneAfter;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * @param capacity         maximum tokens the bucket holds (also the burst size)
     * @param refillPerMinute  sustained refill rate in tokens per minute
     * @param pruneAfter       buckets idle longer than this are dropped from the map
     */
    public LeakyBucket(int capacity, int refillPerMinute, Duration pruneAfter) {
        this(capacity, refillPerMinute, pruneAfter, Clock.systemUTC());
    }

    /** Visible-for-testing constructor that lets tests drive time deterministically. */
    public LeakyBucket(int capacity, int refillPerMinute, Duration pruneAfter, Clock clock) {
        this(capacity, refillIntervalFromPerMinute(refillPerMinute), pruneAfter, clock);
    }

    /**
     * Constructor for rates that don't divide evenly into per-minute tokens — e.g. five
     * tokens per hour. The refill interval is the wall-clock gap between two single-token
     * top-ups; an interval of 12 minutes yields five tokens per hour.
     *
     * @param capacity       maximum tokens the bucket holds (also the burst size)
     * @param refillInterval wall-clock gap between two single-token refills
     * @param pruneAfter     buckets idle longer than this are dropped from the map
     * @param clock          clock used for refill / prune timing (visible for tests)
     */
    public LeakyBucket(int capacity, Duration refillInterval, Duration pruneAfter, Clock clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be >= 1");
        if (refillInterval.isZero() || refillInterval.isNegative()) {
            throw new IllegalArgumentException("refillInterval must be positive");
        }
        this.capacity = capacity;
        this.refillInterval = refillInterval;
        this.pruneAfter = pruneAfter;
        this.clock = clock;
    }

    private static Duration refillIntervalFromPerMinute(int refillPerMinute) {
        if (refillPerMinute < 1) throw new IllegalArgumentException("refillPerMinute must be >= 1");
        return Duration.ofSeconds(60).dividedBy(refillPerMinute);
    }

    /**
     * Attempts to consume one token from the bucket for {@code key}.
     *
     * @return empty when the request is allowed, or the seconds until the next token refills
     *         when the caller should be rate-limited
     */
    public Optional<Long> tryAcquire(String key) {
        Instant now = clock.instant();
        // Opportunistic GC: every call has a tiny chance of evicting stale buckets.
        if (Math.random() < 0.001) prune(now);

        var admitted = new boolean[1];
        var retryAfterSeconds = new long[1];
        buckets.compute(key, (k, existing) -> {
            Bucket bucket = existing != null ? existing : new Bucket(capacity, now);
            bucket.refill(now, refillInterval, capacity);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                admitted[0] = true;
            } else {
                // Round up so we never lie to clients about an immediate retry.
                double missing = 1.0 - bucket.tokens;
                long millis = (long) Math.ceil(missing * refillInterval.toMillis());
                retryAfterSeconds[0] = Math.max(1L, (millis + 999) / 1000);
            }
            return bucket;
        });
        return admitted[0] ? Optional.empty() : Optional.of(retryAfterSeconds[0]);
    }

    private void prune(Instant now) {
        var threshold = now.minus(pruneAfter);
        buckets.entrySet().removeIf(e -> e.getValue().lastRefill.isBefore(threshold));
    }

    /**
     * Per-key bucket state. {@code tokens} is a fractional count because refill is
     * continuous, not in whole-unit ticks — keeping it floating point gives a steady
     * refill curve without losing tiny remainders between calls.
     */
    private static final class Bucket {
        double tokens;
        Instant lastRefill;

        Bucket(double tokens, Instant lastRefill) {
            this.tokens = tokens;
            this.lastRefill = lastRefill;
        }

        void refill(Instant now, Duration refillInterval, int capacity) {
            if (!now.isAfter(lastRefill)) return;
            long elapsedMs = Duration.between(lastRefill, now).toMillis();
            double added = elapsedMs / (double) refillInterval.toMillis();
            tokens = Math.min(capacity, tokens + added);
            lastRefill = now;
        }
    }
}
