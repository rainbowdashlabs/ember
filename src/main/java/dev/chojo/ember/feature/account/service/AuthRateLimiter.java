/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.util.LeakyBucket;
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
 * <p>Each endpoint family has up to two dimensions — one bucket keyed by the resolved
 * client IP and (where the request carries an identity) a second bucket keyed by a
 * SHA-256 hash of the lowercased email or the numeric account id. The most-recently
 * exhausted bucket's retry-after wins; the caller sees a single seconds value.
 *
 * <p>State is in-memory; a restart resets every bucket. Clustered deployments would
 * need a shared backing store — tracked as a follow-up alongside the federation
 * replay cache.
 */
@Singleton
public class AuthRateLimiter {

    private static final Duration MINUTE = Duration.ofMinutes(1);
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

    public AuthRateLimiter() {
        this(Clock.systemUTC());
    }

    /** Visible-for-testing constructor that lets tests drive time deterministically. */
    public AuthRateLimiter(Clock clock) {
        this.loginIp = new LeakyBucket(10, 10, PRUNE_AFTER, clock);
        this.loginIdentity = new LeakyBucket(20, FIFTEEN_MIN.dividedBy(5), PRUNE_AFTER, clock);
        this.registerIp = new LeakyBucket(5, FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.forgotIp = new LeakyBucket(3, FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.forgotIdentity = new LeakyBucket(3, HOUR, PRUNE_AFTER, clock);
        this.resendIp = new LeakyBucket(3, FIFTEEN_MIN, PRUNE_AFTER, clock);
        this.resendIdentity = new LeakyBucket(3, HOUR, PRUNE_AFTER, clock);
        this.verifyIp = new LeakyBucket(30, 30, PRUNE_AFTER, clock);
        this.setPasswordIp = new LeakyBucket(30, 30, PRUNE_AFTER, clock);
        this.confirmEmailIp = new LeakyBucket(30, 30, PRUNE_AFTER, clock);
        this.refreshIp = new LeakyBucket(60, 60, PRUNE_AFTER, clock);
        this.changePasswordIdentity = new LeakyBucket(10, HOUR.dividedBy(5), PRUNE_AFTER, clock);
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
}
