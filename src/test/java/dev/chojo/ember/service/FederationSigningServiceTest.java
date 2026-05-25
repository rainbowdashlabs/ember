/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.federation.service.FederationSigningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class FederationSigningServiceTest {

    private static FederationSigningService signingService;
    private static PrivateKey privateKey;
    private static PublicKey publicKey;

    @BeforeAll
    static void setup() throws Exception {
        signingService = new FederationSigningService();
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var keyPair = generator.generateKeyPair();
        privateKey = keyPair.getPrivate();
        publicKey = keyPair.getPublic();
    }

    @Test
    void signAndVerifyRoundTrip() {
        String body = "{\"hello\":\"world\"}";
        Instant timestamp = Instant.now();
        String signature = signingService.sign(body, timestamp.toString(), privateKey);

        assertNotNull(signature);
        assertTrue(signingService.verify(body, signature, publicKey, timestamp));
    }

    @Test
    void rejectTamperedMessage() {
        String body = "{\"hello\":\"world\"}";
        Instant timestamp = Instant.now();
        String signature = signingService.sign(body, timestamp.toString(), privateKey);

        assertFalse(signingService.verify("{\"hello\":\"tampered\"}", signature, publicKey, timestamp));
    }

    @Test
    void rejectExpiredTimestamp() {
        String body = "{\"data\":\"test\"}";
        Instant oldTimestamp = Instant.now().minusSeconds(600); // 10 minutes ago
        String signature = signingService.sign(body, oldTimestamp.toString(), privateKey);

        assertFalse(signingService.verify(body, signature, publicKey, oldTimestamp));
    }

    @Test
    void acceptValidTimestampWithinWindow() {
        String body = "{\"data\":\"test\"}";
        Instant recentTimestamp = Instant.now().minusSeconds(120); // 2 minutes ago
        String signature = signingService.sign(body, recentTimestamp.toString(), privateKey);

        assertTrue(signingService.verify(body, signature, publicKey, recentTimestamp));
    }

    @Test
    void rejectFutureTimestampBeyondWindow() {
        String body = "{\"data\":\"test\"}";
        Instant futureTimestamp = Instant.now().plusSeconds(600); // 10 minutes in future
        String signature = signingService.sign(body, futureTimestamp.toString(), privateKey);

        assertFalse(signingService.verify(body, signature, publicKey, futureTimestamp));
    }

    @Test
    void decodeAndVerifyWithEncodedKeys() {
        String body = "test payload";
        Instant timestamp = Instant.now();

        // Encode keys to Base64
        String encodedPublic = java.util.Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String encodedPrivate = java.util.Base64.getEncoder().encodeToString(privateKey.getEncoded());

        // Decode them back
        PublicKey decodedPublic = signingService.decodePublicKey(encodedPublic);
        PrivateKey decodedPrivate = signingService.decodePrivateKey(encodedPrivate);

        String signature = signingService.sign(body, timestamp.toString(), decodedPrivate);
        assertTrue(signingService.verify(body, signature, decodedPublic, timestamp));
    }

    @Test
    void rejectSignatureFromDifferentKey() throws Exception {
        String body = "{\"data\":\"sensitive\"}";
        Instant timestamp = Instant.now();

        // Sign with original key
        String signature = signingService.sign(body, timestamp.toString(), privateKey);

        // Verify with a different key
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var otherKeyPair = generator.generateKeyPair();

        assertFalse(signingService.verify(body, signature, otherKeyPair.getPublic(), timestamp));
    }
}
