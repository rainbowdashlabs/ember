/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MapTileRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void aFullMapViewPassesAndAWalkOfTheGridIsStopped() {
        var clock = new MutableClock();
        var limiter = new MapTileRateLimiter(clock);

        for (int i = 0; i < MapTileRateLimiter.BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire("203.0.113.7").isEmpty(), "tile " + i + " is part of the burst");
        }

        var refused = limiter.tryAcquire("203.0.113.7");
        assertTrue(refused.isPresent());
        assertTrue(refused.get() >= 1);
    }

    @Test
    void theBucketRefillsAndOtherAddressesAreUntouched() {
        var clock = new MutableClock();
        var limiter = new MapTileRateLimiter(clock);
        for (int i = 0; i < MapTileRateLimiter.BURST_CAPACITY; i++) limiter.tryAcquire("203.0.113.7");

        assertTrue(limiter.tryAcquire("198.51.100.1").isEmpty());
        assertTrue(limiter.tryAcquire("203.0.113.7").isPresent());

        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.tryAcquire("203.0.113.7").isEmpty());
    }

    @Test
    void aDevelopmentInstanceIsNotHeldBack() {
        var demo = new Demo() {
            @Override
            public boolean dev() {
                return true;
            }
        };
        var limiter = new MapTileRateLimiter(demo);

        for (int i = 0; i < MapTileRateLimiter.BURST_CAPACITY * 3; i++) {
            assertTrue(limiter.tryAcquire("203.0.113.7").isEmpty());
        }
    }
}
