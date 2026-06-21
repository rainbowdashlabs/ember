/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.feature.twofactor.entity.TrustedDevice;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Mints, validates, and revokes the {@code ember_2fa_trust} cookie. The cookie value is a
 * 32-byte SecureRandom encoded URL-safe; only its HMAC-SHA-256 (via {@link TokenHasher}) lives
 * in {@code account_2fa_trusted_device}.
 */
@Singleton
public class TrustedDeviceService {
    public static final String COOKIE_NAME = "ember_2fa_trust";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final TwoFactorRepository repository;
    private final TokenHasher tokenHasher;
    private final TwoFactorSettings settings;

    @Inject
    public TrustedDeviceService(TwoFactorRepository repository, TokenHasher tokenHasher, TwoFactorSettings settings) {
        this.repository = repository;
        this.tokenHasher = tokenHasher;
        this.settings = settings;
    }

    /**
     * Returns the operator-configured maximum window for trusted-device cookies, capped at 30 days.
     */
    public int maxDays() {
        return settings.trustedDeviceMaxDays();
    }

    public record Issued(String token, TrustedDevice device) {}

    /**
     * Creates a trusted-device row for {@code accountId}. {@code requestedDays} is clamped to
     * {@code [1, maxDays]}; values ≤ 0 are rejected by the caller.
     */
    public Issued issue(int accountId, int requestedDays, String userAgent) {
        int days = Math.min(Math.max(requestedDays, 1), settings.trustedDeviceMaxDays());
        String token = newToken();
        Instant trustedUntil = Instant.now().plus(Duration.ofDays(days));
        var device = repository.createTrustedDevice(accountId, tokenHasher.hash(token), userAgent, trustedUntil);
        return new Issued(token, device);
    }

    /**
     * Looks up a valid trusted-device row by its plaintext cookie. Returns empty when the cookie
     * is missing, malformed, expired, revoked, or unknown. Successful lookups touch
     * {@code last_seen_at}.
     */
    public Optional<TrustedDevice> validate(String cookieValue) {
        if (cookieValue == null || cookieValue.isBlank()) return Optional.empty();
        var hit = repository.findTrustedDeviceByHash(tokenHasher.hash(cookieValue));
        hit.ifPresent(d -> repository.touchTrustedDevice(d.id()));
        return hit;
    }

    public List<TrustedDevice> list(int accountId) {
        return repository.findActiveTrustedDevices(accountId);
    }

    public boolean revoke(int deviceId, int accountId) {
        return repository.revokeTrustedDevice(deviceId, accountId);
    }

    public void revokeAll(int accountId) {
        repository.revokeAllTrustedDevices(accountId);
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
