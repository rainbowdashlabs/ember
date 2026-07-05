/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * Handles request signing and verification for cross-instance federation.
 * <p>
 * Signatures are RSA SHA-256 over a canonical envelope that binds the HTTP method,
 * request path (including a sorted query string), the recipient station UUID, the
 * per-request nonce, the timestamp and the request body. Binding the method, path
 * and recipient prevents a captured signature from being replayed against a
 * different endpoint or peer within the timestamp window. Binding the nonce means
 * an attacker cannot swap in a fresh nonce to sidestep the per-partner replay
 * check in {@link FederationReplayCache}.
 * <p>
 * The handshake exchange that establishes a federation predates the request
 * envelope; it uses {@link #signEnrollmentPayload(String, PrivateKey)} /
 * {@link #verifyEnrollmentPayload(String, String, PublicKey)} which sign the raw
 * payload bytes with no envelope.
 */
@Singleton
public class FederationSigningService {
    private static final Logger log = LoggerFactory.getLogger(FederationSigningService.class);
    private static final String ALGORITHM = "SHA256withRSA";
    private static final Duration MAX_TIMESTAMP_DRIFT = Duration.ofMinutes(5);
    private static final int MIN_RSA_KEY_BITS = 2048;

    /**
     * Builds the canonical path-with-query string by sorting {@code &}-separated
     * pairs lexicographically. The path is used verbatim; the query is omitted
     * entirely when the request has no query string.
     */
    public static String canonicalPathWithQuery(String path, String query) {
        String safePath = path == null ? "" : path;
        if (query == null || query.isEmpty()) {
            return safePath;
        }
        String[] pairs = query.split("&");
        Arrays.sort(pairs);
        return safePath + "?" + String.join("&", pairs);
    }

    /**
     * Convenience overload that derives the canonical path-with-query from a
     * fully-qualified request URI.
     */
    public static String canonicalPathWithQuery(URI uri) {
        return canonicalPathWithQuery(uri.getRawPath(), uri.getRawQuery());
    }

    /**
     * Reports whether the query string repeats any parameter name. Because the
     * canonical form sorts {@code &}-separated pairs, {@code a=1&a=2} and
     * {@code a=2&a=1} would share one signature while a handler reads wire order;
     * the receiver rejects any request with duplicate keys to close that gap.
     */
    public static boolean hasDuplicateQueryKeys(String query) {
        if (query == null || query.isEmpty()) {
            return false;
        }
        var seen = new java.util.HashSet<String>();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if (!seen.add(key)) {
                return true;
            }
        }
        return false;
    }

    private static String canonicalEnvelope(
            String method, String pathWithQuery, UUID recipientUuid, String nonce, String timestamp, String body) {
        return method.toUpperCase(Locale.ROOT)
                + "\n"
                + (pathWithQuery == null ? "" : pathWithQuery)
                + "\n"
                + recipientUuid
                + "\n"
                + (nonce == null ? "" : nonce)
                + "\n"
                + timestamp
                + "\n"
                + (body == null ? "" : body);
    }

    /**
     * Signs a federation request with the given private key.
     *
     * @param method        uppercase HTTP method (GET, POST, …)
     * @param pathWithQuery path plus canonical (sorted) query string; see
     *                      {@link #canonicalPathWithQuery(String, String)}
     * @param recipientUuid the UUID of the station that will receive the request
     * @param nonce         the per-request replay nonce sent in the
     *                      {@code X-Federation-Nonce} header
     * @param body          the request body (use {@code ""} for empty)
     * @param timestamp     the request timestamp (ISO-8601)
     * @param privateKey    the signing RSA private key
     * @return Base64-encoded signature over the canonical envelope
     */
    public String sign(
            String method,
            String pathWithQuery,
            UUID recipientUuid,
            String nonce,
            String body,
            String timestamp,
            PrivateKey privateKey) {
        try {
            var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(canonicalEnvelope(method, pathWithQuery, recipientUuid, nonce, timestamp, body)
                    .getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request", e);
        }
    }

    /**
     * Verifies a signed federation request using the partner's public key.
     * Rejects requests outside the {@code ±5 min} timestamp window.
     *
     * @param method        uppercase HTTP method
     * @param pathWithQuery path plus canonical (sorted) query string
     * @param recipientUuid the UUID of the receiving station (i.e. this instance's own)
     * @param nonce         the per-request replay nonce from the
     *                      {@code X-Federation-Nonce} header
     * @param body          the request body
     * @param signature     the Base64-encoded signature
     * @param publicKey     the sender's RSA public key
     * @param timestamp     the request timestamp
     * @return true iff the signature matches and the timestamp is within the window
     */
    public boolean verify(
            String method,
            String pathWithQuery,
            UUID recipientUuid,
            String nonce,
            String body,
            String signature,
            PublicKey publicKey,
            Instant timestamp) {
        var now = Instant.now();
        if (Duration.between(timestamp, now).abs().compareTo(MAX_TIMESTAMP_DRIFT) > 0) {
            log.warn("Federation request rejected: timestamp drift too large ({} vs {})", timestamp, now);
            return false;
        }

        try {
            var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(canonicalEnvelope(method, pathWithQuery, recipientUuid, nonce, timestamp.toString(), body)
                    .getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("Federation signature verification failed", e);
            return false;
        }
    }

    /**
     * Signs a handshake / enrollment payload. The handshake exchange precedes
     * the establishment of the partner record, so it cannot use the request
     * envelope (no recipient UUID is known yet).
     */
    public String signEnrollmentPayload(String payload, PrivateKey privateKey) {
        try {
            var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign enrollment payload", e);
        }
    }

    /**
     * Verifies a handshake / enrollment payload signature.
     */
    public boolean verifyEnrollmentPayload(String payload, String signature, PublicKey publicKey) {
        try {
            var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(payload.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("Federation enrollment signature verification failed", e);
            return false;
        }
    }

    /**
     * Decodes a Base64-encoded RSA public key. Rejects keys weaker than
     * {@value #MIN_RSA_KEY_BITS} bits so a partner cannot register a trivially
     * factorable key.
     */
    public PublicKey decodePublicKey(String base64Key) {
        try {
            var keyBytes = Base64.getDecoder().decode(base64Key);
            var spec = new X509EncodedKeySpec(keyBytes);
            var key = KeyFactory.getInstance("RSA").generatePublic(spec);
            if (key instanceof RSAKey rsaKey && rsaKey.getModulus().bitLength() < MIN_RSA_KEY_BITS) {
                throw new IllegalArgumentException("RSA public key must be at least " + MIN_RSA_KEY_BITS + " bits");
            }
            return key;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode public key", e);
        }
    }

    /**
     * Decodes a Base64-encoded RSA private key.
     */
    public PrivateKey decodePrivateKey(String base64Key) {
        try {
            var keyBytes = Base64.getDecoder().decode(base64Key);
            var spec = new PKCS8EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode private key", e);
        }
    }
}
