/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
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
    void twoFactorIpBucketExhaustsAtBurst() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 20; i++) {
            assertTrue(limiter.tryTwoFactor("1.2.3.4", i).isEmpty());
        }
        var retry = limiter.tryTwoFactor("1.2.3.4", 999);
        assertTrue(retry.isPresent());
        assertTrue(retry.get() > 0);
    }

    @Test
    void twoFactorIdentityBucketExhaustsAcrossIps() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++) {
            assertTrue(limiter.tryTwoFactor("10.0.0." + i, 42).isEmpty());
        }
        var retry = limiter.tryTwoFactor("10.99.99.99", 42);
        assertTrue(retry.isPresent(), "11th attempt for same account should be limited even from a new IP");
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
    void theInjectedConstructorCreatesWorkingInstance() throws Exception {
        var limiter = new AuthRateLimiter(demo(false), new Auth());
        assertTrue(limiter.tryLogin("9.9.9.9", "test@example.com").isEmpty());
    }

    /**
     * A development instance runs the whole suite and every hand test from one address, so buckets
     * meant for a stranger grinding an endpoint would only ever catch the person working on it. The
     * public demo is on the internet and keeps every limit it has.
     */
    @Test
    void aDevelopmentInstanceIsNotThrottledAndAPublicDemoStillIs() throws Exception {
        var onDev = new AuthRateLimiter(demo(true), new Auth());
        for (int i = 0; i < 200; i++) {
            assertTrue(onDev.tryDeviceRequest("1.2.3.4", "a@example.com").isEmpty(), "a dev run never meets a bucket");
        }

        var onDemo = new AuthRateLimiter(demo(false, true), new Auth());
        for (int i = 0; i < 10; i++) onDemo.tryDeviceRequest("1.2.3.4", "a@example.com");
        assertTrue(
                onDemo.tryDeviceRequest("1.2.3.4", "a@example.com").isPresent(),
                "the public demo is exactly where an unthrottled auth surface would be found");
    }

    private static Demo demo(boolean dev) throws Exception {
        return demo(dev, false);
    }

    private static Demo demo(boolean dev, boolean enabled) throws Exception {
        var demo = new Demo();
        for (var name : new String[] {"dev", "enabled"}) {
            Field field = Demo.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(demo, name.equals("dev") ? dev : enabled);
        }
        return demo;
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

    @Test
    void passkeySignInLimitsByIp() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++)
            assertTrue(limiter.tryPasskeySignIn("1.2.3.4").isEmpty());
        assertTrue(limiter.tryPasskeySignIn("1.2.3.4").isPresent());
        assertTrue(limiter.tryPasskeySignIn("5.6.7.8").isEmpty(), "the anonymous bucket is per address");
    }

    @Test
    void passwordStepUpLimitsByAccountAcrossAddresses() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);
        for (int i = 0; i < 10; i++)
            assertTrue(limiter.tryPasswordStepUp("10.0.0." + i, 42).isEmpty());
        assertTrue(
                limiter.tryPasswordStepUp("10.99.99.99", 42).isPresent(),
                "the oracle is throttled per account, not per address");
    }

    @Test
    void theDeviceHandshakeBucketsHoldTheirLines() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 10; i++)
            assertTrue(limiter.tryDeviceRequest("1.2.3.4", "one@example.com").isEmpty());
        assertTrue(limiter.tryDeviceRequest("1.2.3.4", "one@example.com").isPresent());

        for (int i = 0; i < 90; i++)
            assertTrue(limiter.tryDevicePoll("1.2.3.4", "secret-a").isEmpty());
        assertTrue(limiter.tryDevicePoll("1.2.3.4", "secret-a").isPresent());

        for (int i = 0; i < 40; i++)
            assertTrue(limiter.tryDeviceEnroll("1.2.3.4").isEmpty());
        assertTrue(limiter.tryDeviceEnroll("1.2.3.4").isPresent());

        // Three code entries a minute per session, and five approvals an hour per account.
        for (int i = 0; i < 3; i++) assertTrue(limiter.tryDeviceCodeEntry(7, 42).isEmpty());
        assertTrue(limiter.tryDeviceCodeEntry(7, 43).isPresent(), "the fourth entry on one session is refused");
        assertTrue(limiter.tryDeviceCodeEntry(8, 42).isEmpty(), "a fresh session gets its own three");
        assertTrue(limiter.tryDeviceCodeEntry(9, 42).isEmpty());
        assertTrue(
                limiter.tryDeviceCodeEntry(10, 42).isPresent(),
                "the sixth approval for one account is refused whoever's session asks");
    }

    /**
     * The office. Everybody behind one router shares an address, so keying the wait on the address
     * meant two people signing in at once held each other refused for as long as they both waited:
     * a waiting device asks about twenty-four times a minute and the bucket refilled forty.
     *
     * <p>The poll secret is handed out by the request that created it and nobody else holds it, so
     * it tells two waiting devices apart where the address cannot.
     */
    @Test
    void twoDevicesBehindOneAddressDoNotSpendEachOthersPolls() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 90; i++) limiter.tryDevicePoll("1.2.3.4", "secret-a");
        assertTrue(limiter.tryDevicePoll("1.2.3.4", "secret-a").isPresent(), "the first device spent its own");

        assertTrue(
                limiter.tryDevicePoll("1.2.3.4", "secret-b").isEmpty(),
                "the second device behind the same router waits on nobody");
    }

    /** The same office, on the other bucket: raising a request is counted against the account. */
    @Test
    void twoPeopleBehindOneAddressDoNotSpendEachOthersRequests() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 10; i++) limiter.tryDeviceRequest("1.2.3.4", "one@example.com");
        assertTrue(limiter.tryDeviceRequest("1.2.3.4", "one@example.com").isPresent());

        assertTrue(
                limiter.tryDeviceRequest("1.2.3.4", "two@example.com").isEmpty(),
                "a colleague at the next desk has their own budget");
    }

    /**
     * The address bucket is still there, and still stops one machine opening requests for every
     * address it knows. It is only deep enough that an office never reaches it.
     */
    @Test
    void oneMachineCannotRaiseRequestsForEverybody() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 60; i++) limiter.tryDeviceRequest("1.2.3.4", "person" + i + "@example.com");

        assertTrue(
                limiter.tryDeviceRequest("1.2.3.4", "someone-else@example.com").isPresent(),
                "the address runs out even though every account was fresh");
    }

    /**
     * Confirming a sensitive action and signing in share nothing. They used to draw on one bucket,
     * so an afternoon of confirmations quietly spent that evening's sign-in budget.
     */
    @Test
    void aStepUpDoesNotSpendTheBudgetForSigningIn() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 20; i++) limiter.tryStepUpDeviceRequest("1.2.3.4", 42);
        assertTrue(limiter.tryStepUpDeviceRequest("1.2.3.4", 42).isPresent(), "the step-ups ran out");

        assertTrue(
                limiter.tryDeviceRequest("5.6.7.8", "one@example.com").isEmpty(), "and signing in is untouched by it");
    }

    /**
     * A poll secret is a bearer token and base64 is case sensitive, so two different secrets must
     * not fold into one bucket the way two spellings of an address are meant to.
     */
    @Test
    void twoSecretsDifferingOnlyInCaseAreTwoBuckets() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 90; i++) limiter.tryDevicePoll("1.2.3.4", "aBc");
        assertTrue(limiter.tryDevicePoll("1.2.3.4", "aBc").isPresent());

        assertTrue(limiter.tryDevicePoll("1.2.3.4", "abc").isEmpty(), "a different secret is a different bucket");
    }

    /**
     * An address that belongs to nobody still counts against its own bucket. Were it not counted,
     * the difference between a request that was throttled and one that was not would answer whether
     * an address has an account here.
     */
    @Test
    void anIdentifierThatMatchesNothingIsCountedLikeAnyOther() {
        var clock = new ControllableClock(Instant.parse("2026-06-12T10:00:00Z"));
        var limiter = new AuthRateLimiter(clock);

        for (int i = 0; i < 10; i++) limiter.tryDeviceRequest("1.2.3.4", "nobody@example.com");

        assertTrue(limiter.tryDeviceRequest("1.2.3.4", "nobody@example.com").isPresent());
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
