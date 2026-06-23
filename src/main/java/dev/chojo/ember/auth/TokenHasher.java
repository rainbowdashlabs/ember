/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Computes the at-rest hash of a bearer-shaped token (session bearers,
 * password-reset codes, email-verification codes, station-delete codes).
 *
 * <p>Stored tokens are hashed with {@code HMAC-SHA-256} using a server-side
 * pepper loaded from {@link Auth#tokenPepper()}. A database-only compromise
 * (SQL injection, backup leak, replica misconfiguration) does not yield usable
 * tokens — the attacker also needs the application secret.
 *
 * <p>The raw token format is unchanged on the wire. Only the database write
 * path is hashed; lookups by raw token go through {@link #hash(String)} before
 * the {@code WHERE} clause.
 */
@Singleton
public class TokenHasher {

    private static final String ALGORITHM = "HmacSHA256";

    /**
     * Fallback pepper used during {@code demo.dev} / {@code demo.enabled} runs
     * when the operator has not set {@link Auth#tokenPepper()}. Production
     * deployments must configure a real pepper or the application refuses to
     * boot.
     */
    private static final String DEV_PLACEHOLDER_PEPPER = "ember-dev-pepper-not-for-production";

    private final byte[] pepper;

    @Inject
    public TokenHasher(Auth auth, Demo demo) {
        this(resolvePepper(auth, demo));
    }

    private TokenHasher(String resolvedPepper) {
        this.pepper = resolvedPepper.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Constructs a {@link TokenHasher} with an explicit pepper. Intended for
     * unit and repository tests that need a deterministic hash without wiring
     * a full {@link Auth} / {@link Demo} configuration.
     */
    public static TokenHasher forTesting(String pepper) {
        Objects.requireNonNull(pepper, "pepper");
        if (pepper.isBlank()) {
            throw new IllegalArgumentException("pepper must not be blank");
        }
        return new TokenHasher(pepper);
    }

    private static String resolvePepper(Auth auth, Demo demo) {
        String configured = auth.tokenPepper();
        if (configured == null || configured.isBlank()) {
            if (!demo.dev() && !demo.enabled()) {
                throw new IllegalStateException(
                        "auth.tokenPepper (or AUTH_TOKEN_PEPPER) must be set in production deployments. "
                                + "Generate a long random value and inject it via configuration.");
            }
            return DEV_PLACEHOLDER_PEPPER;
        }
        return configured;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /**
     * Returns the lowercase hex encoding of {@code HMAC-SHA-256(pepper, rawToken)}.
     * The output is exactly 64 characters and column-stable, suitable for
     * {@code WHERE token_hash = :hash} comparison.
     *
     * @param rawToken the bearer value supplied by the client; never {@code null}.
     */
    public String hash(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(pepper, ALGORITHM));
            byte[] digest = mac.doFinal(rawToken.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA-256 unavailable on this JVM", e);
        }
    }
}
