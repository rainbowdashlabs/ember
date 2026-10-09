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
import java.time.ZoneId;
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
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final String PLAIN = "/api/v1/station/members";
    private static final String AI = "/api/v1/station/ai/quiz";
    private static final String SEAL_CHECK = "/api/v1/public/signing/verify";

    @Test
    void allowsBurstThenThrottles() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 900; i++) {
            assertTrue(limiter.check("1.2.3.4", PLAIN).isEmpty());
        }
        assertFalse(limiter.check("1.2.3.4", PLAIN).isEmpty());
    }

    @Test
    void separateIpsHaveSeparateBudgets() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 900; i++) {
            limiter.check("1.1.1.1", PLAIN);
        }
        assertTrue(limiter.check("2.2.2.2", PLAIN).isEmpty());
    }

    @Test
    void expensivePathHasTighterBudget() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < 40; i++) {
            assertTrue(limiter.check("9.9.9.9", AI).isEmpty());
        }
        assertFalse(limiter.check("9.9.9.9", AI).isEmpty());
    }

    /** A seal check carries a large file and holds one of very few slots, so its burst is far smaller than AI's. */
    @Test
    void checkingSealsHasTheSmallestBurstOfItsOwn() {
        var clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var limiter = new GlobalRateLimiter(clock);

        for (int i = 0; i < GlobalRateLimiter.SEAL_CHECK_BURST; i++) {
            assertTrue(limiter.check("8.8.8.8", SEAL_CHECK).isEmpty());
        }
        assertFalse(limiter.check("8.8.8.8", SEAL_CHECK).isEmpty());
        assertTrue(limiter.check("8.8.8.8", AI).isEmpty(), "the checks take nothing from the AI budget");
        assertTrue(GlobalRateLimiter.SEAL_CHECK_BURST < 40);

        clock.advance(Duration.ofSeconds(10));
        assertTrue(limiter.check("8.8.8.8", SEAL_CHECK).isEmpty(), "and refill at their own pace");
    }

    @Test
    void generatingWithAiAndCheckingSealsAreTheExpensivePaths() {
        assertTrue(GlobalRateLimiter.isExpensive(AI));
        assertTrue(GlobalRateLimiter.isSealCheck(SEAL_CHECK));
        assertFalse(GlobalRateLimiter.isExpensive(SEAL_CHECK));
        assertFalse(GlobalRateLimiter.isSealCheck("/api/v1/public/signing/ca"));
        assertFalse(GlobalRateLimiter.isExpensive(PLAIN));
        assertFalse(GlobalRateLimiter.isSealCheck(PLAIN));
    }
}
