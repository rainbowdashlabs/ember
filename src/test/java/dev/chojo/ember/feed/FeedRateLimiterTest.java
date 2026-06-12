/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feed;

import dev.chojo.ember.feature.feed.FeedRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FeedRateLimiterTest {

    @Test
    void firstAcquireAlwaysAllowed() {
        var limiter = new FeedRateLimiter();
        assertTrue(limiter.tryAcquire("token-a").isEmpty());
    }

    @Test
    void secondAcquireWithinWindowReportsRetryAfter() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        assertTrue(limiter.tryAcquire("t").isEmpty());

        // 30s later, still inside the 60s window → rejected with positive Retry-After.
        clock.advanceSeconds(30);
        var retry = limiter.tryAcquire("t");
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
        assertTrue(retry.get() <= 60);
    }

    @Test
    void acquireAfterWindowIsAllowed() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        assertTrue(limiter.tryAcquire("t").isEmpty());

        // > 60s later — window has passed.
        clock.advanceSeconds(61);
        assertTrue(limiter.tryAcquire("t").isEmpty());
    }

    @Test
    void differentTokensHaveSeparateBuckets() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        assertTrue(limiter.tryAcquire("a").isEmpty());
        assertTrue(limiter.tryAcquire("b").isEmpty());

        clock.advanceSeconds(5);
        assertTrue(limiter.tryAcquire("a").isPresent());
        assertTrue(limiter.tryAcquire("b").isPresent());
    }

    @Test
    void concurrentRequestsResolveExactlyOneAdmission() throws Exception {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new FeedRateLimiter(clock);
        int threads = 32;
        var ready = new java.util.concurrent.CountDownLatch(threads);
        var go = new java.util.concurrent.CountDownLatch(1);
        var admitted = new java.util.concurrent.atomic.AtomicInteger();

        var pool = java.util.concurrent.Executors.newFixedThreadPool(threads);
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
            assertTrue(pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, admitted.get(), "Exactly one concurrent request should pass through the bucket");
    }

    // -- helpers --

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
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        void advanceSeconds(long s) {
            now.updateAndGet(i -> i.plusSeconds(s));
        }
    }
}
