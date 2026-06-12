/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed;

import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-token rate limiter for the personal feed endpoints. Feeds are not real-time, so one
 * request per minute per token is plenty for any well-behaved reader while shielding the
 * backend from misbehaving aggregators (RSS-Bridge instances, runaway cron jobs).
 *
 * <p>The limiter shares a single bucket across {@code events.ics},
 * {@code notifications.rss}, and {@code notifications.atom} because all three hit the same
 * backing data — a reader polling every endpoint each minute is the worst case we want to
 * permit. The token-scoped image endpoint is intentionally exempt: a single feed body may
 * link many images and they have their own 24 h cache.
 *
 * <p>State is held in-memory. A restart resets every counter; that's fine — the worst case
 * is a brief permissive window where pollers' first requests succeed. Old buckets are
 * pruned opportunistically so the map cannot grow without bound.
 */
@Singleton
public class FeedRateLimiter {
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Instant> lastAllowed = new ConcurrentHashMap<>();
    private final Clock clock;

    public FeedRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Visible-for-testing constructor that lets tests drive time deterministically. */
    public FeedRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * Attempts to consume one token slot for {@code token}.
     *
     * @return empty when the request is allowed, or the remaining cool-down in seconds when
     *         the caller should be served {@code 429} with {@code Retry-After}
     */
    public Optional<Long> tryAcquire(String token) {
        Instant now = clock.instant();
        // Opportunistic GC: every call has a tiny chance of evicting stale buckets so a
        // long-running instance cannot leak entries for revoked or single-use tokens.
        if (Math.random() < 0.001) prune(now);

        // compute() runs the remapping under a per-key lock so concurrent callers cannot both
        // pass through the same window. We capture the previous value in a single-element
        // holder so we can reason about whether *this* call was the one that won.
        var prevHolder = new Instant[1];
        lastAllowed.compute(token, (k, existing) -> {
            prevHolder[0] = existing;
            if (existing == null || now.isAfter(existing.plus(WINDOW))) return now;
            return existing;
        });
        Instant prev = prevHolder[0];
        if (prev == null || now.isAfter(prev.plus(WINDOW))) return Optional.empty();
        long remaining = WINDOW.minus(Duration.between(prev, now)).toSeconds();
        return Optional.of(Math.max(remaining, 1L));
    }

    private void prune(Instant now) {
        var threshold = now.minus(PRUNE_AFTER);
        lastAllowed.entrySet().removeIf(e -> e.getValue().isBefore(threshold));
    }
}
