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
 * Per-address rate limiter for the public map tile endpoint, backed by a {@link LeakyBucket}.
 *
 * <p>Every tile the cache does not hold yet is fetched upstream on the operator's API key, and the
 * endpoint answers anybody. A map view needs a few dozen tiles at once and panning or zooming asks
 * for a few dozen more, so the bucket admits {@value #BURST_CAPACITY} tiles immediately and refills
 * at {@value #REFILL_PER_MINUTE} per minute. That is several full views in a row for a reader and a
 * hard ceiling for a script walking the tile grid.
 *
 * <p>A development instance keeps the machinery with a capacity nothing reaches, the same as the
 * other public limiters, so the end-to-end suite never trips it from its one address.
 */
@Singleton
public class MapTileRateLimiter {

    /** Tiles admitted immediately from an idle bucket: about four full map views. */
    public static final int BURST_CAPACITY = 200;

    /** Sustained refill: five tiles a second. */
    public static final int REFILL_PER_MINUTE = 300;

    private static final int DEV_CAPACITY = 1_000_000;
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket bucket;

    @Inject
    public MapTileRateLimiter(Demo demoConfig) {
        this(Clock.systemUTC(), demoConfig.dev() ? DEV_CAPACITY : BURST_CAPACITY);
    }

    /**
     * A limiter on a clock the caller drives, at the production capacity.
     *
     * @param clock the clock refills are measured on
     */
    public MapTileRateLimiter(Clock clock) {
        this(clock, BURST_CAPACITY);
    }

    private MapTileRateLimiter(Clock clock, int capacity) {
        this.bucket = new LeakyBucket(capacity, REFILL_PER_MINUTE, PRUNE_AFTER, clock);
    }

    /**
     * Attempts to take one tile for the given address.
     *
     * @param clientIp the resolved address of the caller
     * @return empty when the tile is allowed, or the seconds until the next refill when the caller
     *     should be served {@code 429} with {@code Retry-After}
     */
    public Optional<Long> tryAcquire(String clientIp) {
        return bucket.tryAcquire(clientIp);
    }
}
