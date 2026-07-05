/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Coarse per-client-IP rate limiter for the whole API surface, complementing the
 * finer-grained {@code AuthRateLimiter} on the authentication endpoints.
 *
 * <p>Two buckets per IP: a generous global bucket sized for the bursty request
 * pattern of a single-page app, and a much tighter bucket applied on top for
 * known-expensive endpoints (AI generation) so one client cannot monopolise the
 * costly work. Both admit bursts up to their capacity and then throttle to the
 * sustained refill rate.
 *
 * <p>State is in-memory; a restart resets every bucket. Clustered deployments
 * would need a shared backing store — the same follow-up tracked for the auth
 * limiter and the federation replay cache.
 */
@Singleton
public class GlobalRateLimiter {
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket global;
    private final LeakyBucket expensive;

    public GlobalRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * Visible-for-testing constructor that lets tests drive time deterministically.
     */
    public GlobalRateLimiter(Clock clock) {
        this.global = new LeakyBucket(900, 600, PRUNE_AFTER, clock);
        this.expensive = new LeakyBucket(40, 20, PRUNE_AFTER, clock);
    }

    /**
     * Consumes a token for {@code clientIp}. When {@code expensivePath} is set, the
     * stricter expensive-endpoint bucket must also admit the request.
     *
     * @return empty when the request is allowed, or the seconds to wait before retrying
     */
    public Optional<Long> check(String clientIp, boolean expensivePath) {
        Optional<Long> globalRetry = global.tryAcquire(clientIp);
        if (globalRetry.isPresent()) {
            return globalRetry;
        }
        if (expensivePath) {
            return expensive.tryAcquire("expensive:" + clientIp);
        }
        return Optional.empty();
    }
}
