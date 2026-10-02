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
 * An in-memory per-key token bucket: {@code capacity} requests at once, refilled continuously, so the
 * burst a page makes on opening passes while the sustained rate stays bounded.
 *
 * <p>A restart resets every bucket. Idle buckets are pruned now and then, so short-lived keys cannot grow
 * the map without bound. Each key is updated under {@link ConcurrentHashMap#compute}, so two callers never
 * share the last token.
 */
public final class LeakyBucket {

    private final int capacity;
    private final Duration refillInterval;
    private final Duration pruneAfter;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Creates a bucket per key.
     *
     * @param capacity        maximum tokens the bucket holds (also the burst size)
     * @param refillPerMinute sustained refill rate in tokens per minute
     * @param pruneAfter      buckets idle longer than this are dropped from the map
     */
    public LeakyBucket(int capacity, int refillPerMinute, Duration pruneAfter) {
        this(capacity, refillPerMinute, pruneAfter, Clock.systemUTC());
    }

    /** As {@link #LeakyBucket(int, int, Duration)}, reading time from the clock. */
    public LeakyBucket(int capacity, int refillPerMinute, Duration pruneAfter, Clock clock) {
        this(capacity, refillIntervalFromPerMinute(refillPerMinute), pruneAfter, clock);
    }

    /**
     * For rates that are not whole tokens per minute: an interval of 12 minutes gives five tokens an hour.
     *
     * @param refillInterval the time between two single-token refills
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
     * Takes one token from the key's bucket; about one call in a thousand also prunes idle buckets.
     *
     * @return empty when allowed, otherwise the whole seconds until the next token, rounded up and at least one
     */
    public Optional<Long> tryAcquire(String key) {
        Instant now = clock.instant();
        if (Math.random() < 0.001) prune(now);

        var admitted = new boolean[1];
        var retryAfterSeconds = new long[1];
        buckets.compute(key, (_, existing) -> {
            Bucket bucket = existing != null ? existing : new Bucket(capacity, now);
            bucket.refill(now, refillInterval, capacity);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                admitted[0] = true;
            } else {
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

    /** One key's state; fractional tokens keep the continuous refill from losing remainders. */
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
