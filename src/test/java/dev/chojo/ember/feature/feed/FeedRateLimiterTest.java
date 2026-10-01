/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FeedRateLimiterTest {

    @Test
    void firstAcquireAlwaysAllowed() {
        var limiter = new FeedRateLimiter();
        assertTrue(limiter.tryAcquire("token-a").isEmpty());
    }

    /** The burst drains the bucket, and nothing refills within the same instant. */
    @Test
    void initialBurstAdmitsCapacityThenRejects() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire("t").isEmpty(), "Admission " + (i + 1) + " should pass");
        }
        var retry = limiter.tryAcquire("t");
        assertTrue(retry.isPresent(), "11th request immediately after a burst should be rate-limited");
        assertTrue(retry.get() > 0);
        assertTrue(retry.get() <= 12, "Retry-After should be within one refill interval");
    }

    @Test
    void slotsRefillAtTheConfiguredRate() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire("t").isEmpty());
        }
        assertTrue(limiter.tryAcquire("t").isPresent());

        clock.advanceSeconds(12);
        assertTrue(limiter.tryAcquire("t").isEmpty(), "A token should have refilled after one interval");
        assertTrue(limiter.tryAcquire("t").isPresent(), "But only one - the bucket is empty again");
    }

    @Test
    void longIdleRefillsUpToCapacityNotBeyond() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++) limiter.tryAcquire("t");
        clock.advanceSeconds(60 * 100);
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++) {
            assertTrue(limiter.tryAcquire("t").isEmpty(), "Refilled burst slot " + (i + 1));
        }
        assertTrue(limiter.tryAcquire("t").isPresent(), "Bucket should not exceed capacity even after a long idle");
    }

    @Test
    void differentTokensHaveSeparateBuckets() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++)
            assertTrue(limiter.tryAcquire("a").isEmpty());
        assertTrue(limiter.tryAcquire("a").isPresent());
        for (int i = 0; i < FeedRateLimiter.BURST_CAPACITY; i++)
            assertTrue(limiter.tryAcquire("b").isEmpty());
        assertTrue(limiter.tryAcquire("b").isPresent());
    }

    @Test
    void concurrentRequestsRespectTheBucketCap() throws Exception {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        int threads = 64;
        var ready = new CountDownLatch(threads);
        var go = new CountDownLatch(1);
        var admitted = new AtomicInteger();

        var pool = Executors.newFixedThreadPool(threads);
        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    if (limiter.tryAcquire("contention").isEmpty()) admitted.incrementAndGet();
                    return null;
                });
            }
            ready.await();
            go.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(
                FeedRateLimiter.BURST_CAPACITY,
                admitted.get(),
                "Exactly the burst capacity should be admitted under contention");
    }

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
