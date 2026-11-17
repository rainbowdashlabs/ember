/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.service;

import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Address lookups are limited per account, and a development instance never trips the limit. */
class GeocodingRateLimiterTest {

    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void anAccountIsStoppedAfterItsBurstAndAnotherIsNot() {
        var limiter = new GeocodingRateLimiter(FIXED);
        for (int i = 0; i < GeocodingRateLimiter.BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire(1).isEmpty(), "lookup " + i + " is part of the burst");
        }
        assertTrue(limiter.tryAcquire(1).isPresent());
        assertTrue(limiter.tryAcquire(2).isEmpty());
    }

    @Test
    void aDevelopmentInstanceIsNotHeldToTheBurst() {
        var demo = mock(Demo.class);
        when(demo.dev()).thenReturn(true);
        var limiter = new GeocodingRateLimiter(demo);
        for (int i = 0; i <= GeocodingRateLimiter.BURST_CAPACITY * 3; i++) {
            assertTrue(limiter.tryAcquire(1).isEmpty());
        }
    }

    @Test
    void aProductionInstanceIsHeldToTheBurst() {
        var demo = mock(Demo.class);
        when(demo.dev()).thenReturn(false);
        var limiter = new GeocodingRateLimiter(demo);
        for (int i = 0; i < GeocodingRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1);
        }
        assertTrue(limiter.tryAcquire(1).isPresent());
    }
}
