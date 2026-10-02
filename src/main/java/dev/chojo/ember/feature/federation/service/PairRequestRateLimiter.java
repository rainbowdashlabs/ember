/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * Bounds the requests to federate this instance takes from other instances.
 *
 * <p>Two buckets, for the two ways the endpoint can be worked. One instance sending request after
 * request is caught per instance key. Many instances asking the same station at once, which buries
 * its managers in requests, is caught per station.
 *
 * <p>A development instance keeps the machinery with a capacity nothing reaches, so the end-to-end
 * suite can send its requests on every run without waiting out an hour.
 */
@Singleton
public class PairRequestRateLimiter {

    /** Requests one instance may send per hour, to all stations here together. */
    public static final int PER_INSTANCE_CAPACITY = 20;

    /** Requests one station here may receive per hour, from all instances together. */
    public static final int PER_STATION_CAPACITY = 10;

    private static final int DEV_CAPACITY = 1_000_000;
    private static final Duration PRUNE_AFTER = Duration.ofHours(2);

    private final LeakyBucket perInstance;
    private final LeakyBucket perStation;

    @Inject
    public PairRequestRateLimiter(Demo demoConfig) {
        this(
                Clock.systemUTC(),
                demoConfig.dev() ? DEV_CAPACITY : PER_INSTANCE_CAPACITY,
                demoConfig.dev() ? DEV_CAPACITY : PER_STATION_CAPACITY);
    }

    /**
     * Builds the limiter with the production capacities on the given clock.
     *
     * @param clock the clock the buckets refill by
     */
    public PairRequestRateLimiter(Clock clock) {
        this(clock, PER_INSTANCE_CAPACITY, PER_STATION_CAPACITY);
    }

    private PairRequestRateLimiter(Clock clock, int instanceCapacity, int stationCapacity) {
        this.perInstance =
                new LeakyBucket(instanceCapacity, Duration.ofMinutes(60 / PER_INSTANCE_CAPACITY), PRUNE_AFTER, clock);
        this.perStation =
                new LeakyBucket(stationCapacity, Duration.ofMinutes(60 / PER_STATION_CAPACITY), PRUNE_AFTER, clock);
    }

    /**
     * Counts a request from the given instance.
     *
     * @param instanceKey the sending instance's discovery key
     * @return empty when it is admitted, or the seconds until the next one would be
     */
    public Optional<Long> tryInstance(String instanceKey) {
        return perInstance.tryAcquire("instance:" + instanceKey);
    }

    /**
     * Counts a request for the given station.
     *
     * @param stationId the asked station
     * @return empty when it is admitted, or the seconds until the next one would be
     */
    public Optional<Long> tryStation(int stationId) {
        return perStation.tryAcquire("station:" + stationId);
    }
}
