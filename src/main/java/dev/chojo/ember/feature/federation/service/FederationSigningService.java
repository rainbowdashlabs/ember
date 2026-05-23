/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Handles request signing and verification for cross-instance federation.
 * Uses SHA256withRSA signatures with timestamp-based replay attack prevention.
 */
@Singleton
public class FederationSigningService {
    private static final Logger log = LoggerFactory.getLogger(FederationSigningService.class);
    private static final String ALGORITHM = "SHA256withRSA";
    private static final Duration MAX_TIMESTAMP_DRIFT = Duration.ofMinutes(5);

    /**
     * Signs a request body with the given private key.
     * The signature covers body + timestamp to prevent replay attacks.
     *
     * @param body       the request body to sign
     * @param timestamp  the request timestamp (ISO-8601)
     * @param privateKey the RSA private key
     * @return Base64-encoded signature
     */
    public String sign(String body, String timestamp, PrivateKey privateKey) {
        try {
            var signer = Signature.getInstance(ALGORITHM);
            signer.initSign(privateKey);
            signer.update((timestamp + "\n" + body).getBytes());
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign request", e);
        }
    }

    /**
     * Verifies a signed request body using the partner's public key.
     * Rejects requests with timestamps older than 5 minutes.
     *
     * @param body      the request body
     * @param signature the Base64-encoded signature
     * @param publicKey the partner's RSA public key
     * @param timestamp the request timestamp
     * @return true if signature is valid and timestamp is within window
     */
    public boolean verify(String body, String signature, PublicKey publicKey, Instant timestamp) {
        // Check timestamp window
        var now = Instant.now();
        if (Duration.between(timestamp, now).abs().compareTo(MAX_TIMESTAMP_DRIFT) > 0) {
            log.warn("Federation request rejected: timestamp drift too large ({} vs {})", timestamp, now);
            return false;
        }

        try {
            var verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update((timestamp.toString() + "\n" + body).getBytes());
            return verifier.verify(Base64.getDecoder().decode(signature));
        } catch (Exception e) {
            log.warn("Federation signature verification failed", e);
            return false;
        }
    }

    /**
     * Decodes a Base64-encoded RSA public key.
     */
    public PublicKey decodePublicKey(String base64Key) {
        try {
            var keyBytes = Base64.getDecoder().decode(base64Key);
            var spec = new X509EncodedKeySpec(keyBytes);
            return KeyFactory.getInstance("RSA").generatePublic(spec);
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
