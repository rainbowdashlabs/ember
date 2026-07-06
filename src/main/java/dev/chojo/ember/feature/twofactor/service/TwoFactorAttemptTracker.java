/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.inject.Singleton;

import java.time.Duration;

/**
 * Tracks failed second-factor verification attempts per key (a hashed pre-auth token or an
 * account id) so a single pre-auth token cannot be reused for an unbounded number of TOTP
 * guesses within its validity window.
 *
 * <p>State is in-memory and expires shortly after the pre-auth token would; a restart resets
 * every counter. Clustered deployments would need a shared backing store — tracked as a
 * follow-up alongside the rate limiter and federation replay cache.
 */
@Singleton
public class TwoFactorAttemptTracker {

    /**
     * Number of failed proofs tolerated before the associated pre-auth token is consumed and
     * the caller must obtain a fresh one by re-authenticating with their password.
     */
    public static final int MAX_ATTEMPTS = 5;

    private final Cache<String, Integer> failures = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(100_000)
            .build();

    /**
     * Records a failed verification for the given key and returns the running failure count.
     *
     * @param key a stable identifier for the challenge (e.g. the hashed pre-auth token)
     * @return the number of failures recorded for this key, including the current one
     */
    public int recordFailure(String key) {
        return failures.asMap().merge(key, 1, Integer::sum);
    }

    /**
     * Clears the failure count for the given key after a successful verification.
     */
    public void reset(String key) {
        failures.invalidate(key);
    }
}
