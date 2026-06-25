/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Per-(form, submitter_hash) rate limiter for anonymous public form submissions.
 * Keyed off the hash, never the raw IP, so the limit survives across requests
 * without ever persisting an IP.
 *
 * <p>Defaults: {@value #BURST_CAPACITY} submissions admitted from an idle bucket
 * with sustained refill of {@value #REFILL_PER_HOUR} per hour. The cap of
 * "5 submissions per hour per (form, hash)" matches the spec. A misbehaving
 * scraper that wins one burst gets at most {@code BURST_CAPACITY +
 * REFILL_PER_HOUR} submissions in a sliding hour.
 */
@Singleton
public class PublicFormRateLimiter {

    public static final int BURST_CAPACITY = 5;
    public static final int REFILL_PER_HOUR = 5;
    private static final Duration PRUNE_AFTER = Duration.ofHours(2);

    private final LeakyBucket bucket;

    public PublicFormRateLimiter() {
        this(Clock.systemUTC());
    }

    public PublicFormRateLimiter(Clock clock) {
        Duration refillInterval = Duration.ofMinutes(60 / REFILL_PER_HOUR);
        this.bucket = new LeakyBucket(BURST_CAPACITY, refillInterval, PRUNE_AFTER, clock);
    }

    /**
     * @return empty when the request is allowed, or the seconds until the next refill
     * when the caller should be served {@code 429} with {@code Retry-After}
     */
    public Optional<Long> tryAcquire(int formId, byte[] submitterHash) {
        return bucket.tryAcquire(formId + ":" + HexFormat.of().formatHex(submitterHash));
    }
}
