/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.waitinglist.service;

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

class PublicWaitingListRateLimiterTest {

    private static final String CODE = "invite-code";

    private static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-08-24T12:00:00Z");

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

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @Test
    void oneAddressIsAdmittedUpToItsCapacity() {
        var limiter = new PublicWaitingListRateLimiter(new MutableClock());

        for (int i = 0; i < PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY; i++) {
            assertFalse(limiter.tryAcquire("198.51.100.7", CODE).isPresent(), "registration " + i);
        }

        var retry = limiter.tryAcquire("198.51.100.7", CODE);
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
    }

    @Test
    void anotherAddressStartsWithAFullBucket() {
        var limiter = new PublicWaitingListRateLimiter(new MutableClock());
        for (int i = 0; i < PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY; i++) {
            limiter.tryAcquire("198.51.100.7", CODE);
        }

        assertFalse(limiter.tryAcquire("203.0.113.9", CODE).isPresent());
    }

    /**
     * A code on a poster is worked from many addresses at once, which the per-address bucket cannot
     * see. The invite bucket is what keeps that from draining the instance's mail budget.
     */
    @Test
    void oneInviteIsAdmittedUpToItsCapacityAcrossAddresses() {
        var limiter = new PublicWaitingListRateLimiter(new MutableClock());

        for (int i = 0; i < PublicWaitingListRateLimiter.PER_INVITE_CAPACITY; i++) {
            assertFalse(limiter.tryAcquire("198.51.100." + i, CODE).isPresent(), "registration " + i);
        }

        assertTrue(limiter.tryAcquire("198.51.100.200", CODE).isPresent());
        assertFalse(limiter.tryAcquire("198.51.100.201", "another-code").isPresent());
    }

    /**
     * The injected limiter takes its capacities from the instance kind: an open one gets the real
     * ceiling, a development one a ceiling nothing reaches, so the end-to-end suite can register
     * through the same invite on every run.
     */
    @Test
    void theInjectedLimiterFollowsTheInstanceKind() {
        var open = mock(Demo.class);
        when(open.dev()).thenReturn(false);
        var openLimiter = new PublicWaitingListRateLimiter(open);
        for (int i = 0; i < PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY; i++) {
            assertFalse(openLimiter.tryAcquire("198.51.100.7", CODE).isPresent());
        }
        assertTrue(openLimiter.tryAcquire("198.51.100.7", CODE).isPresent());

        var development = mock(Demo.class);
        when(development.dev()).thenReturn(true);
        var devLimiter = new PublicWaitingListRateLimiter(development);
        for (int i = 0; i < PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY + 1; i++) {
            assertFalse(devLimiter.tryAcquire("198.51.100.7", CODE).isPresent(), "registration " + i);
        }
    }

    @Test
    void refillRestoresOneRegistration() {
        var clock = new MutableClock();
        var limiter = new PublicWaitingListRateLimiter(clock);
        for (int i = 0; i < PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY; i++) {
            limiter.tryAcquire("198.51.100.7", CODE);
        }
        assertTrue(limiter.tryAcquire("198.51.100.7", CODE).isPresent());

        clock.advance(Duration.ofMinutes(60 / PublicWaitingListRateLimiter.PER_ADDRESS_CAPACITY));

        assertFalse(limiter.tryAcquire("198.51.100.7", CODE).isPresent());
    }
}
