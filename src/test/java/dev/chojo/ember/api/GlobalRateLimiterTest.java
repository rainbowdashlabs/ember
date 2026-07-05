/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalRateLimiterTest {

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration d) {
            now = now.plus(d);
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
    void allowsBurstThenThrottles() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 900; i++) {
            assertTrue(limiter.check("1.2.3.4", false).isEmpty());
        }
        assertFalse(limiter.check("1.2.3.4", false).isEmpty());
    }

    @Test
    void separateIpsHaveSeparateBudgets() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 900; i++) {
            limiter.check("1.1.1.1", false);
        }
        assertTrue(limiter.check("2.2.2.2", false).isEmpty());
    }

    @Test
    void expensivePathHasTighterBudget() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 40; i++) {
            assertTrue(limiter.check("9.9.9.9", true).isEmpty());
        }
        assertFalse(limiter.check("9.9.9.9", true).isEmpty());
    }
}
