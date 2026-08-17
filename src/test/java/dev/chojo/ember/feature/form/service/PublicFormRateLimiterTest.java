/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.service;

import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormRateLimiterTest {

    private static final byte[] HASH_A = new byte[] {1, 2, 3, 4};
    private static final byte[] HASH_B = new byte[] {5, 6, 7, 8};

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-06-16T12:00:00Z");

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

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    @Test
    void noArgConstructorUsesSystemClock() {
        // Just exercise the default constructor — verifies it wires up without throwing.
        var limiter = new PublicFormRateLimiter();
        assertFalse(limiter.tryAcquire(1, HASH_A).isPresent());
    }

    @Test
    void burstUpToCapacityIsAdmitted() {
        var clock = new MutableClock();
        var limiter = new PublicFormRateLimiter(clock);
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            assertFalse(limiter.tryAcquire(1, HASH_A).isPresent(), "iteration " + i + " should pass");
        }
    }

    @Test
    void burstPlusOneIsRejectedWithRetryAfter() {
        var clock = new MutableClock();
        var limiter = new PublicFormRateLimiter(clock);
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1, HASH_A);
        }
        var retry = limiter.tryAcquire(1, HASH_A);
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
    }

    @Test
    void separateHashesHaveSeparateBuckets() {
        var clock = new MutableClock();
        var limiter = new PublicFormRateLimiter(clock);
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1, HASH_A);
        }
        // Same form, different hash — fresh bucket.
        assertFalse(limiter.tryAcquire(1, HASH_B).isPresent());
    }

    @Test
    void separateFormsHaveSeparateBuckets() {
        var clock = new MutableClock();
        var limiter = new PublicFormRateLimiter(clock);
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1, HASH_A);
        }
        // Different form, same hash — fresh bucket.
        assertFalse(limiter.tryAcquire(2, HASH_A).isPresent());
    }

    @Test
    void refillRestoresOneSlotAfterInterval() {
        var clock = new MutableClock();
        var limiter = new PublicFormRateLimiter(clock);
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1, HASH_A);
        }
        assertTrue(limiter.tryAcquire(1, HASH_A).isPresent());

        // After one refill interval (60 / REFILL_PER_HOUR minutes), one slot should be back.
        clock.advance(Duration.ofMinutes(60 / PublicFormRateLimiter.REFILL_PER_HOUR));
        assertFalse(limiter.tryAcquire(1, HASH_A).isPresent());
        // And only one — a second request immediately afterwards is again rate-limited.
        assertTrue(limiter.tryAcquire(1, HASH_A).isPresent());
    }

    @Test
    void devInstanceAdmitsFarMoreThanTheBurst() {
        var demo = mock(Demo.class);
        when(demo.dev()).thenReturn(true);
        var limiter = new PublicFormRateLimiter(demo);

        // The end-to-end suite answers the same public form on every run from one address, so a dev
        // instance keeps the machinery and lifts the ceiling out of reach.
        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY * 10; i++) {
            assertFalse(limiter.tryAcquire(1, HASH_A).isPresent(), "iteration " + i + " should pass");
        }
    }

    @Test
    void anInstanceThatIsNotDevKeepsTheNormalBurst() {
        var demo = mock(Demo.class);
        when(demo.dev()).thenReturn(false);
        var limiter = new PublicFormRateLimiter(demo);

        for (int i = 0; i < PublicFormRateLimiter.BURST_CAPACITY; i++) {
            limiter.tryAcquire(1, HASH_A);
        }
        assertTrue(limiter.tryAcquire(1, HASH_A).isPresent());
    }
}
