/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LeakyBucketTest {

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(0, 5, Duration.ofHours(1)));
        assertThrows(IllegalArgumentException.class, () -> new LeakyBucket(10, 0, Duration.ofHours(1)));
    }

    @Test
    void initialBurstAdmitsCapacity() {
        var bucket = new LeakyBucket(3, 60, Duration.ofMinutes(10), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isPresent(), "4th acquire must be rate-limited");
    }

    @Test
    void refillIsContinuous() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        // 2 capacity, 60/min → 1 token / sec.
        var bucket = new LeakyBucket(2, 60, Duration.ofMinutes(10), clock);
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isPresent());

        clock.advanceSeconds(1);
        assertTrue(bucket.tryAcquire("k").isEmpty(), "After 1s a token has refilled");
        assertTrue(bucket.tryAcquire("k").isPresent());

        clock.advanceSeconds(10);
        // Capacity cap is honoured - only 2 tokens are available, not 10.
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isEmpty());
        assertTrue(bucket.tryAcquire("k").isPresent(), "Cannot exceed capacity even after a long idle");
    }

    @Test
    void retryAfterCommunicatesPositiveSeconds() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var bucket = new LeakyBucket(1, 6, Duration.ofMinutes(10), clock); // refill every 10s
        assertTrue(bucket.tryAcquire("k").isEmpty());
        var retry = bucket.tryAcquire("k");
        assertTrue(retry.isPresent());
        assertTrue(retry.get() >= 1, "Retry-After should be at least 1 second");
        assertTrue(retry.get() <= 10, "Retry-After should be at most one refill interval");
    }

    @Test
    void keysAreIsolated() {
        var bucket = new LeakyBucket(1, 60, Duration.ofMinutes(10), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertTrue(bucket.tryAcquire("a").isEmpty());
        assertTrue(bucket.tryAcquire("a").isPresent());
        // "b" still has its single slot.
        assertTrue(bucket.tryAcquire("b").isEmpty());
    }

    // -- helper --

    private static final class ControllableClock extends Clock {
        private final AtomicReference<Instant> now;

        ControllableClock(Instant initial) {
            this.now = new AtomicReference<>(initial);
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        void advanceSeconds(long s) {
            now.updateAndGet(i -> i.plusSeconds(s));
        }
    }
}
