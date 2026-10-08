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
 * <p>A generous global bucket per IP, sized for the bursty request pattern of a single-page app, and a
 * much tighter bucket applied on top for each kind of known-expensive endpoint, so one client cannot
 * monopolise the costly work. AI generation has one ({@link #isExpensive(String)}). Checking the seals
 * of an uploaded document has a smaller one of its own ({@link #isSealCheck(String)}): anybody may ask
 * for it without signing in, each request carries up to 25 MB, and the whole server runs only two
 * checks at once. Every bucket admits bursts up to its capacity and then throttles to its sustained
 * refill rate.
 *
 * <p>State is in-memory; a restart resets every bucket. Clustered deployments
 * would need a shared backing store - the same follow-up tracked for the auth
 * limiter and the federation replay cache.
 */
@Singleton
public class GlobalRateLimiter {
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    /** How many seal checks one address may send at once before it is throttled. */
    static final int SEAL_CHECK_BURST = 6;

    private final LeakyBucket global;
    private final LeakyBucket expensive;
    private final LeakyBucket sealChecks;

    public GlobalRateLimiter() {
        this(Clock.systemUTC());
    }

    /**
     * Visible-for-testing constructor that lets tests drive time deterministically.
     */
    public GlobalRateLimiter(Clock clock) {
        this.global = new LeakyBucket(900, 600, PRUNE_AFTER, clock);
        this.expensive = new LeakyBucket(40, 20, PRUNE_AFTER, clock);
        this.sealChecks = new LeakyBucket(SEAL_CHECK_BURST, 6, PRUNE_AFTER, clock);
    }

    /**
     * Whether a request path generates with AI, which also takes from the tighter bucket.
     *
     * @param path the request path
     * @return whether it is expensive
     */
    public static boolean isExpensive(String path) {
        return path.contains("/ai/");
    }

    /**
     * Whether a request path checks the seals of an uploaded document, which takes from the smallest
     * bucket.
     *
     * @param path the request path
     * @return whether it is a seal check
     */
    public static boolean isSealCheck(String path) {
        return path.endsWith("/public/signing/verify");
    }

    /**
     * Consumes a token for {@code clientIp} from the global bucket, and from the bucket of the path's
     * kind where it is an expensive one.
     *
     * @param clientIp the client's address
     * @param path     the request path
     * @return empty when the request is allowed, or the seconds to wait before retrying
     */
    public Optional<Long> check(String clientIp, String path) {
        Optional<Long> globalRetry = global.tryAcquire(clientIp);
        if (globalRetry.isPresent()) {
            return globalRetry;
        }
        if (isSealCheck(path)) {
            return sealChecks.tryAcquire("seal-check:" + clientIp);
        }
        if (isExpensive(path)) {
            return expensive.tryAcquire("expensive:" + clientIp);
        }
        return Optional.empty();
    }
}
