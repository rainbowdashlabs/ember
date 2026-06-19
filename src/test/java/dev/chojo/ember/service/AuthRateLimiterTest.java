/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {

    @Test
    void loginIpBucketExhaustsAtBurst() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryLogin("1.2.3.4", "user" + i + "@example.com").isEmpty());
        }
        var retry = limiter.tryLogin("1.2.3.4", "fresh@example.com");
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
    }

    @Test
    void loginIdentityBucketExhaustsAcrossIps() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryLogin("10.0.0." + i, "victim@example.com").isEmpty());
        }
        var retry = limiter.tryLogin("10.99.99.99", "victim@example.com");
        assertTrue(retry.isPresent(), "21st attempt for same identity should be limited even from a new IP");
    }

    @Test
    void loginEmailHashIsCaseAndWhitespaceInsensitive() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryLogin("10.0.0." + i, "Victim@Example.com").isEmpty());
        }
        var retry = limiter.tryLogin("10.99.99.99", "  victim@example.COM  ");
        assertTrue(retry.isPresent(), "Identity normalization should treat case/whitespace variants as the same key");
    }

    @Test
    void registerLimitsPerIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryRegister("1.1.1.1").isEmpty());
        assertTrue(limiter.tryRegister("1.1.1.1").isPresent());
    }

    @Test
    void registerDifferentIpsAreIndependent() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryRegister("1.1.1.1").isEmpty());
        for (int i = 0; i < 5; i++) assertTrue(limiter.tryRegister("2.2.2.2").isEmpty());
    }

    @Test
    void clockAdvanceRefillsLoginIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++) limiter.tryLogin("1.2.3.4", "u" + i + "@example.com");
        assertTrue(limiter.tryLogin("1.2.3.4", "x@example.com").isPresent());

        clock.advanceSeconds(7);
        assertTrue(limiter.tryLogin("1.2.3.4", "x@example.com").isEmpty(), "One token should have refilled after 6s");
    }

    @Test
    void changePasswordKeyedByAccountId() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++) assertTrue(limiter.tryChangePassword(42).isEmpty());
        assertTrue(limiter.tryChangePassword(42).isPresent());
        assertTrue(limiter.tryChangePassword(43).isEmpty(), "A different account is independent");
    }

    @Test
    void refreshHasHighCeiling() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 60; i++) assertTrue(limiter.tryRefresh("1.2.3.4").isEmpty());
        assertTrue(limiter.tryRefresh("1.2.3.4").isPresent());
    }

    @Test
    void forgotPasswordLimitsPerIpAndIdentity() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 3; i++)
            assertTrue(limiter.tryForgotPassword("1.2.3.4", "u" + i + "@example.com")
                    .isEmpty());
        assertTrue(limiter.tryForgotPassword("1.2.3.4", "new@example.com").isPresent());
    }

    @Test
    void resendVerificationLimitsPerIpAndIdentity() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 3; i++)
            assertTrue(limiter.tryResendVerification("1.2.3.4", "u" + i + "@example.com")
                    .isEmpty());
        assertTrue(limiter.tryResendVerification("1.2.3.4", "new@example.com").isPresent());
    }

    @Test
    void verifyEmailLimitsPerIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 30; i++)
            assertTrue(limiter.tryVerifyEmail("1.2.3.4").isEmpty());
        assertTrue(limiter.tryVerifyEmail("1.2.3.4").isPresent());
    }

    @Test
    void setPasswordLimitsPerIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 30; i++)
            assertTrue(limiter.trySetPassword("1.2.3.4").isEmpty());
        assertTrue(limiter.trySetPassword("1.2.3.4").isPresent());
    }

    @Test
    void confirmEmailChangeLimitsPerIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 30; i++)
            assertTrue(limiter.tryConfirmEmailChange("1.2.3.4").isEmpty());
        assertTrue(limiter.tryConfirmEmailChange("1.2.3.4").isPresent());
    }

    @Test
    void loginHandlesNullEmail() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        assertTrue(limiter.tryLogin("1.2.3.4", null).isEmpty());
    }

    @Test
    void defaultConstructorCreatesWorkingInstance() {
        var limiter = new AuthRateLimiter();
        assertTrue(limiter.tryLogin("9.9.9.9", "test@example.com").isEmpty());
    }

    @Test
    void retryAfterReturnsLargerOfTwoBuckets() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 20; i++) limiter.tryLogin("10.0.0." + i, "victim@example.com");
        var retry = limiter.tryLogin("10.99.99.99", "victim@example.com");
        assertTrue(retry.isPresent());
        assertTrue(retry.get() >= 1L, "Retry-After should report at least one second");
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
