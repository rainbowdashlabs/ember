/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.util.LeakyBucket;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Per-endpoint rate limiter for the {@code /auth/*} surface.
 *
 * <p>Each endpoint family has up to two dimensions - one bucket keyed by the resolved
 * client IP and (where the request carries an identity) a second bucket keyed by a
 * SHA-256 hash of the lowercased email or the numeric account id. The most-recently
 * exhausted bucket's retry-after wins; the caller sees a single seconds value.
 *
 * <p>State is in-memory; a restart resets every bucket. Clustered deployments would
 * need a shared backing store - tracked as a follow-up alongside the federation
 * replay cache.
 *
 * <p>A development instance is exempt: it runs the whole suite and every hand test from one
 * address, where buckets meant for a stranger grinding an endpoint only ever catch the person
 * working on it. The public demo is not exempt, being on the internet.
 */
@Singleton
public class AuthRateLimiter {

    private static final Duration FIFTEEN_MIN = Duration.ofMinutes(15);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration PRUNE_AFTER = Duration.ofHours(1);

    private final LeakyBucket loginIp;
    private final LeakyBucket loginIdentity;
    private final LeakyBucket registerIp;
    private final LeakyBucket forgotIp;
    private final LeakyBucket forgotIdentity;
    private final LeakyBucket resendIp;
    private final LeakyBucket resendIdentity;
    private final LeakyBucket verifyIp;
    private final LeakyBucket setPasswordIp;
    private final LeakyBucket confirmEmailIp;
    private final LeakyBucket refreshIp;
    private final LeakyBucket changePasswordIdentity;
    private final LeakyBucket twoFactorIp;
    private final LeakyBucket twoFactorIdentity;
    private final LeakyBucket passkeySignInIp;
    private final LeakyBucket stepUpPasswordIp;
    private final LeakyBucket stepUpPasswordIdentity;
    private final LeakyBucket deviceRequestIp;
    private final LeakyBucket devicePollIp;
    private final LeakyBucket deviceEnrollIp;
    private final LeakyBucket deviceCodeEntrySession;
    private final LeakyBucket deviceApproveIdentity;

    @Inject
    public AuthRateLimiter(Demo demo) {
        this(demo.dev(), Clock.systemUTC());
    }

    /**
     * Visible-for-testing constructor that lets tests drive time deterministically. It limits like a
     * real instance: the tests here exist to hold the numbers, so a waiver would empty them.
     */
    public AuthRateLimiter(Clock clock) {
        this(false, clock);
    }

    private AuthRateLimiter(boolean unlimited, Clock clock) {
        this.loginIp = new LeakyBucket(cap(unlimited, 10), 10, PRUNE_AFTER, clock);
        this.loginIdentity = new LeakyBucket(cap(unlimited, 20), FIFTEEN_MIN.dividedBy(5), PRUNE_AFTER, clock);
        this.registerIp = new LeakyBucket(cap(unlimited, 5), FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.forgotIp = new LeakyBucket(cap(unlimited, 3), FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.forgotIdentity = new LeakyBucket(cap(unlimited, 3), HOUR, PRUNE_AFTER, clock);
        this.resendIp = new LeakyBucket(cap(unlimited, 3), FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.resendIdentity = new LeakyBucket(cap(unlimited, 3), HOUR, PRUNE_AFTER, clock);
        this.verifyIp = new LeakyBucket(cap(unlimited, 30), 30, PRUNE_AFTER, clock);
        this.setPasswordIp = new LeakyBucket(cap(unlimited, 30), 30, PRUNE_AFTER, clock);
        this.confirmEmailIp = new LeakyBucket(cap(unlimited, 30), 30, PRUNE_AFTER, clock);
        this.refreshIp = new LeakyBucket(cap(unlimited, 60), 60, PRUNE_AFTER, clock);
        this.changePasswordIdentity = new LeakyBucket(cap(unlimited, 10), HOUR.dividedBy(5), PRUNE_AFTER, clock);
        this.twoFactorIp = new LeakyBucket(cap(unlimited, 20), 20, PRUNE_AFTER, clock);
        this.twoFactorIdentity = new LeakyBucket(cap(unlimited, 10), FIFTEEN_MIN.dividedBy(5), PRUNE_AFTER, clock);
        this.passkeySignInIp = new LeakyBucket(cap(unlimited, 10), 10, PRUNE_AFTER, clock);
        this.stepUpPasswordIp = new LeakyBucket(cap(unlimited, 20), 20, PRUNE_AFTER, clock);
        this.stepUpPasswordIdentity = new LeakyBucket(cap(unlimited, 10), FIFTEEN_MIN.dividedBy(5), PRUNE_AFTER, clock);
        this.deviceRequestIp = new LeakyBucket(cap(unlimited, 5), FIFTEEN_MIN.dividedBy(5), PRUNE_AFTER, clock);
        this.devicePollIp = new LeakyBucket(cap(unlimited, 40), 40, PRUNE_AFTER, clock);
        this.deviceEnrollIp = new LeakyBucket(cap(unlimited, 10), 10, PRUNE_AFTER, clock);
        this.deviceCodeEntrySession =
                new LeakyBucket(cap(unlimited, 3), Duration.ofMinutes(1).dividedBy(3), PRUNE_AFTER, clock);
        this.deviceApproveIdentity = new LeakyBucket(cap(unlimited, 5), HOUR.dividedBy(5), PRUNE_AFTER, clock);
    }

    /**
     * How deep a bucket is, which on a development run is deep enough never to empty.
     *
     * <p>A dev instance runs the whole suite and every hand test from one address, so the buckets
     * that exist to stop a stranger grinding an endpoint instead stop the person working on it: five
     * device requests per address, refilling one every three minutes, is three stories.
     *
     * <p>The waiver is here rather than in each of the twenty methods that ask, so a bucket added
     * later cannot be the one somebody forgot. {@code demo.dev()} alone and never
     * {@code demo.enabled()}: the public demo is on the internet and is exactly where an unthrottled
     * auth surface would be found.
     */
    private static int cap(boolean unlimited, int configured) {
        return unlimited ? Integer.MAX_VALUE : configured;
    }

    /**
     * Returns the larger of the two retry-after values, or empty when both buckets
     * admitted the request. Either bucket being exhausted means the request is denied;
     * we surface the longer wait so the caller knows when they can usefully retry.
     */
    private static Optional<Long> takeMax(Optional<Long> a, Optional<Long> b) {
        if (a.isEmpty() && b.isEmpty()) return Optional.empty();
        return Optional.of(Math.max(a.orElse(0L), b.orElse(0L)));
    }

    private static String hashEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public Optional<Long> tryLogin(String ip, String email) {
        return takeMax(loginIp.tryAcquire(ip), loginIdentity.tryAcquire(hashEmail(email)));
    }

    public Optional<Long> tryRegister(String ip) {
        return registerIp.tryAcquire(ip);
    }

    public Optional<Long> tryForgotPassword(String ip, String email) {
        return takeMax(forgotIp.tryAcquire(ip), forgotIdentity.tryAcquire(hashEmail(email)));
    }

    public Optional<Long> tryResendVerification(String ip, String email) {
        return takeMax(resendIp.tryAcquire(ip), resendIdentity.tryAcquire(hashEmail(email)));
    }

    public Optional<Long> tryVerifyEmail(String ip) {
        return verifyIp.tryAcquire(ip);
    }

    public Optional<Long> trySetPassword(String ip) {
        return setPasswordIp.tryAcquire(ip);
    }

    public Optional<Long> tryConfirmEmailChange(String ip) {
        return confirmEmailIp.tryAcquire(ip);
    }

    public Optional<Long> tryRefresh(String ip) {
        return refreshIp.tryAcquire(ip);
    }

    public Optional<Long> tryChangePassword(int accountId) {
        return changePasswordIdentity.tryAcquire(Integer.toString(accountId));
    }

    /**
     * Throttles second-factor verification and step-up attempts, keyed by both the client IP
     * and the numeric account id, so a stolen password plus a valid pre-auth token cannot be
     * used to sweep the TOTP code space.
     */
    public Optional<Long> tryTwoFactor(String ip, int accountId) {
        return takeMax(twoFactorIp.tryAcquire(ip), twoFactorIdentity.tryAcquire(Integer.toString(accountId)));
    }

    /**
     * Throttles the passwordless sign-in, begin and finish alike, by client IP. The begin as
     * well because it writes a challenge row for any anonymous visitor; there is no account to
     * count by until the assertion comes back.
     */
    public Optional<Long> tryPasskeySignIn(String ip) {
        return passkeySignInIp.tryAcquire(ip);
    }

    /**
     * Throttles the password step-up by account and by real client address. It is a password
     * oracle behind any live session, so it gets its own buckets: the first-factor re-entry's
     * bucket is keyed on a literal string where an address belongs, and one attacker grinding a
     * shared bucket would lock every member out of every guarded screen.
     */
    public Optional<Long> tryPasswordStepUp(String ip, int accountId) {
        return takeMax(stepUpPasswordIp.tryAcquire(ip), stepUpPasswordIdentity.tryAcquire(Integer.toString(accountId)));
    }

    /**
     * Throttles opening a device request, by IP: the requester is unauthenticated and has no
     * device identity to count by, and every request writes a row.
     */
    public Optional<Long> tryDeviceRequest(String ip) {
        return deviceRequestIp.tryAcquire(ip);
    }

    /** Throttles the device request poll, generous enough for one device asking every few seconds. */
    public Optional<Long> tryDevicePoll(String ip) {
        return devicePollIp.tryAcquire(ip);
    }

    /** Throttles the enrolment ceremony a device-code token opens, by IP. */
    public Optional<Long> tryDeviceEnroll(String ip) {
        return deviceEnrollIp.tryAcquire(ip);
    }

    /**
     * Throttles code entries on the approval screen: three a minute per session, and five
     * approvals an hour per account, so neither a hijacked session nor a talked-into member can
     * grind or mass-approve.
     */
    public Optional<Long> tryDeviceCodeEntry(int sessionId, int accountId) {
        return takeMax(
                deviceCodeEntrySession.tryAcquire(Integer.toString(sessionId)),
                deviceApproveIdentity.tryAcquire(Integer.toString(accountId)));
    }
}
