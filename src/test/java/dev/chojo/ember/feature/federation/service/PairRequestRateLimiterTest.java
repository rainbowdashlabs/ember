/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The limits on requests to federate: per sending instance and per asked station, each on its own. */
class PairRequestRateLimiterTest {
    private final PairRequestRateLimiter limiter =
            new PairRequestRateLimiter(Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneOffset.UTC));

    @Test
    void oneInstanceIsStoppedAfterItsShare() {
        for (int i = 0; i < PairRequestRateLimiter.PER_INSTANCE_CAPACITY; i++) {
            assertTrue(limiter.tryInstance("busy").isEmpty());
        }

        assertTrue(limiter.tryInstance("busy").isPresent());
        assertTrue(limiter.tryInstance("quiet").isEmpty(), "another instance still has its own share");
    }

    @Test
    void oneStationIsStoppedAfterItsShare() {
        for (int i = 0; i < PairRequestRateLimiter.PER_STATION_CAPACITY; i++) {
            assertTrue(limiter.tryStation(1).isEmpty());
        }

        assertTrue(limiter.tryStation(1).isPresent());
        assertTrue(limiter.tryStation(2).isEmpty(), "another station still has its own share");
    }

    @Test
    void anInstanceOutsideDevelopmentKeepsTheLimits() {
        var configured = new PairRequestRateLimiter(new Demo());

        for (int i = 0; i < PairRequestRateLimiter.PER_STATION_CAPACITY; i++) {
            assertTrue(configured.tryStation(1).isEmpty());
        }

        assertTrue(configured.tryStation(1).isPresent());
    }
}
