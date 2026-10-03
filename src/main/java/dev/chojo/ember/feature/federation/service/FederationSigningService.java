/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.auth.signing.FederationEnvelope;
import dev.chojo.ember.auth.signing.RawBodyEnvelope;
import dev.chojo.ember.auth.signing.SignatureAlgorithm;
import dev.chojo.ember.auth.signing.SignedRequests;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * Federation's use of the shared signed-request module: RSA SHA-256 over the
 * {@link FederationEnvelope}, taken within five minutes of its timestamp.
 * <p>
 * The envelope binds the HTTP method, request path (including a sorted query string), the
 * recipient station UUID, the per-request nonce, the timestamp and the request body. Binding the
 * method, path and recipient prevents a captured signature from being replayed against a
 * different endpoint or peer within the timestamp window. Binding the nonce means an attacker
 * cannot swap in a fresh nonce to sidestep the per-partner replay check.
 * <p>
 * The handshake exchange that establishes a federation predates the request
 * envelope; it uses {@link #signEnrollmentPayload(String, PrivateKey)} /
 * {@link #verifyEnrollmentPayload(String, String, PublicKey)} which sign the raw
 * payload bytes with no envelope.
 */
@Singleton
public class FederationSigningService {
    private static final Logger log = LoggerFactory.getLogger(FederationSigningService.class);
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.RSA_SHA256;
    private static final Duration MAX_TIMESTAMP_DRIFT = Duration.ofMinutes(5);
    private static final int MIN_RSA_KEY_BITS = 2048;

    private final Cache<String, PublicKey> decodedPublicKeys = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(30))
            .maximumSize(10_000)
            .build();

    /**
     * Builds the canonical path-with-query string; see
     * {@link FederationEnvelope#canonicalPathWithQuery(String, String)}.
     */
    public static String canonicalPathWithQuery(String path, @Nullable String query) {
        return FederationEnvelope.canonicalPathWithQuery(path, query);
    }

    /**
     * Derives the canonical path-with-query from a fully-qualified request URI.
     */
    public static String canonicalPathWithQuery(URI uri) {
        return FederationEnvelope.canonicalPathWithQuery(uri);
    }

    /**
     * Reports whether the query string repeats any parameter name; see
     * {@link FederationEnvelope#hasDuplicateQueryKeys(String)}.
     */
    public static boolean hasDuplicateQueryKeys(@Nullable String query) {
        return FederationEnvelope.hasDuplicateQueryKeys(query);
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
        return SignedRequests.sign(
                ALGORITHM,
                privateKey,
                new FederationEnvelope(method, pathWithQuery, recipientUuid, nonce, timestamp, body));
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
        if (!SignedRequests.withinDrift(timestamp, now, MAX_TIMESTAMP_DRIFT)) {
            log.warn("Federation request rejected: timestamp drift too large ({} vs {})", timestamp, now);
            return false;
        }
        var envelope = new FederationEnvelope(method, pathWithQuery, recipientUuid, nonce, timestamp.toString(), body);
        if (!SignedRequests.verify(ALGORITHM, publicKey, envelope, signature)) {
            log.warn("Federation signature verification failed");
            return false;
        }
        return true;
    }

    /**
     * Signs a handshake / enrollment payload. The handshake exchange precedes
     * the establishment of the partner record, so it cannot use the request
     * envelope (no recipient UUID is known yet).
     */
    public String signEnrollmentPayload(String payload, PrivateKey privateKey) {
        return SignedRequests.sign(ALGORITHM, privateKey, new RawBodyEnvelope(payload));
    }

    /**
     * Verifies a handshake / enrollment payload signature.
     */
    public boolean verifyEnrollmentPayload(String payload, String signature, PublicKey publicKey) {
        if (!SignedRequests.verify(ALGORITHM, publicKey, new RawBodyEnvelope(payload), signature)) {
            log.warn("Federation enrollment signature verification failed");
            return false;
        }
        return true;
    }

    /**
     * Whether an enrollment signature fits the key it came with, where the key is still in the form
     * it travelled in. Never throws: a missing part, a key that does not decode and a signature that
     * does not read all answer {@code false}, since each comes from the other side.
     *
     * @param payload    what was signed
     * @param signature  the signature, as sent
     * @param encodedKey the Base64 public key, as sent
     * @return true when the signature fits
     */
    public boolean enrollmentSignatureHolds(String payload, @Nullable String signature, @Nullable String encodedKey) {
        if (signature == null || encodedKey == null) return false;
        try {
            return verifyEnrollmentPayload(payload, signature, decodePublicKey(encodedKey));
        } catch (RuntimeException e) {
            log.warn("An enrollment signature came with a key or in a form that could not be read");
            return false;
        }
    }

    /**
     * Decodes a Base64-encoded RSA public key. Rejects keys weaker than
     * {@value #MIN_RSA_KEY_BITS} bits so a partner cannot register a trivially
     * factorable key.
     *
     * <p>Every signed request from a partner needs its key, so decoded keys are kept for a while,
     * keyed by the encoded form: a partner whose stored key changes simply misses the cache.
     * Keys that fail to decode are never cached.
     */
    public PublicKey decodePublicKey(String base64Key) {
        return decodedPublicKeys.get(base64Key, FederationSigningService::parsePublicKey);
    }

    private static PublicKey parsePublicKey(String base64Key) {
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
}
