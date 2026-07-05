/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FederationSigningServiceTest {

    private static FederationSigningService signingService;
    private static PrivateKey privateKey;
    private static PublicKey publicKey;
    private static final UUID RECIPIENT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/remote/foo";
    private static final String NONCE = "33333333-3333-3333-3333-333333333333";

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
        String signature = signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        assertNotNull(signature);
        assertTrue(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void rejectTamperedBody() {
        String body = "{\"hello\":\"world\"}";
        Instant timestamp = Instant.now();
        String signature = signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        assertFalse(signingService.verify(
                "POST", PATH, RECIPIENT, NONCE, "{\"hello\":\"tampered\"}", signature, publicKey, timestamp));
    }

    @Test
    void rejectMethodMismatch() {
        String body = "";
        Instant timestamp = Instant.now();
        String signature = signingService.sign("GET", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        assertFalse(signingService.verify("DELETE", PATH, RECIPIENT, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void rejectPathMismatch() {
        String body = "";
        Instant timestamp = Instant.now();
        String signature = signingService.sign("GET", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        assertFalse(
                signingService.verify("GET", PATH + "/other", RECIPIENT, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void rejectRecipientMismatch() {
        String body = "";
        Instant timestamp = Instant.now();
        UUID otherRecipient = UUID.fromString("22222222-2222-2222-2222-222222222222");
        String signature = signingService.sign("GET", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        assertFalse(signingService.verify("GET", PATH, otherRecipient, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void rejectNonceMismatch() {
        String body = "{\"data\":\"test\"}";
        Instant timestamp = Instant.now();
        String signature = signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        String otherNonce = "44444444-4444-4444-4444-444444444444";
        assertFalse(signingService.verify("POST", PATH, RECIPIENT, otherNonce, body, signature, publicKey, timestamp));
    }

    @Test
    void rejectExpiredTimestamp() {
        String body = "{\"data\":\"test\"}";
        Instant oldTimestamp = Instant.now().minusSeconds(600);
        String signature =
                signingService.sign("POST", PATH, RECIPIENT, NONCE, body, oldTimestamp.toString(), privateKey);

        assertFalse(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, publicKey, oldTimestamp));
    }

    @Test
    void acceptValidTimestampWithinWindow() {
        String body = "{\"data\":\"test\"}";
        Instant recentTimestamp = Instant.now().minusSeconds(120);
        String signature =
                signingService.sign("POST", PATH, RECIPIENT, NONCE, body, recentTimestamp.toString(), privateKey);

        assertTrue(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, publicKey, recentTimestamp));
    }

    @Test
    void rejectFutureTimestampBeyondWindow() {
        String body = "{\"data\":\"test\"}";
        Instant futureTimestamp = Instant.now().plusSeconds(600);
        String signature =
                signingService.sign("POST", PATH, RECIPIENT, NONCE, body, futureTimestamp.toString(), privateKey);

        assertFalse(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, publicKey, futureTimestamp));
    }

    @Test
    void decodeAndVerifyWithEncodedKeys() {
        String body = "test payload";
        Instant timestamp = Instant.now();

        String encodedPublic = Base64.getEncoder().encodeToString(publicKey.getEncoded());
        String encodedPrivate = Base64.getEncoder().encodeToString(privateKey.getEncoded());

        PublicKey decodedPublic = signingService.decodePublicKey(encodedPublic);
        PrivateKey decodedPrivate = signingService.decodePrivateKey(encodedPrivate);

        String signature =
                signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), decodedPrivate);
        assertTrue(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, decodedPublic, timestamp));
    }

    @Test
    void rejectSignatureFromDifferentKey() throws Exception {
        String body = "{\"data\":\"sensitive\"}";
        Instant timestamp = Instant.now();

        String signature = signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);

        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var otherKeyPair = generator.generateKeyPair();

        assertFalse(signingService.verify(
                "POST", PATH, RECIPIENT, NONCE, body, signature, otherKeyPair.getPublic(), timestamp));
    }

    @Test
    void signEmptyBody() {
        String body = "";
        Instant timestamp = Instant.now();
        String signature = signingService.sign("GET", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);
        assertNotNull(signature);
        assertTrue(signingService.verify("GET", PATH, RECIPIENT, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void verifyRejectsInvalidBase64Signature() {
        String body = "{\"test\":true}";
        Instant timestamp = Instant.now();
        assertFalse(signingService.verify(
                "POST", PATH, RECIPIENT, NONCE, body, "not-valid-base64!!!", publicKey, timestamp));
    }

    @Test
    void decodePublicKeyInvalid() {
        assertThrows(RuntimeException.class, () -> signingService.decodePublicKey("not-a-valid-key"));
    }

    @Test
    void decodePrivateKeyInvalid() {
        assertThrows(RuntimeException.class, () -> signingService.decodePrivateKey("not-a-valid-key"));
    }

    @Test
    void signAndVerifyLargeBodyWithUtf8() {
        String body = "äöü".repeat(5000);
        Instant timestamp = Instant.now();
        String signature = signingService.sign("POST", PATH, RECIPIENT, NONCE, body, timestamp.toString(), privateKey);
        assertTrue(signingService.verify("POST", PATH, RECIPIENT, NONCE, body, signature, publicKey, timestamp));
    }

    @Test
    void canonicalPathSortsQueryPairs() {
        String canonical = FederationSigningService.canonicalPathWithQuery("/api/v1/x", "z=3&a=1&m=2");
        assertEquals("/api/v1/x?a=1&m=2&z=3", canonical);
    }

    @Test
    void canonicalPathHandlesNullQuery() {
        assertEquals("/api/v1/x", FederationSigningService.canonicalPathWithQuery("/api/v1/x", null));
        assertEquals("/api/v1/x", FederationSigningService.canonicalPathWithQuery("/api/v1/x", ""));
    }

    @Test
    void canonicalPathFromUriSortsQueryPairs() {
        var uri = URI.create("https://example.com/api/v1/x?z=3&a=1&m=2");
        assertEquals("/api/v1/x?a=1&m=2&z=3", FederationSigningService.canonicalPathWithQuery(uri));
    }

    @Test
    void canonicalPathFromUriHandlesNoQuery() {
        var uri = URI.create("https://example.com/api/v1/x");
        assertEquals("/api/v1/x", FederationSigningService.canonicalPathWithQuery(uri));
    }

    @Test
    void canonicalPathHandlesNullPath() {
        assertEquals("", FederationSigningService.canonicalPathWithQuery(null, null));
    }

    @Test
    void signAndVerifyWithNullBody() {
        Instant timestamp = Instant.now();
        String signature = signingService.sign("GET", PATH, RECIPIENT, NONCE, null, timestamp.toString(), privateKey);
        assertNotNull(signature);
        assertTrue(signingService.verify("GET", PATH, RECIPIENT, NONCE, null, signature, publicKey, timestamp));
    }

    @Test
    void verifyEnrollmentRejectsInvalidBase64() {
        assertFalse(signingService.verifyEnrollmentPayload("payload", "not-valid!!!", publicKey));
    }

    @Test
    void enrollmentPayloadRoundTrip() {
        String payload = "42:abc:somepubkey";
        String signature = signingService.signEnrollmentPayload(payload, privateKey);
        assertTrue(signingService.verifyEnrollmentPayload(payload, signature, publicKey));
        assertFalse(signingService.verifyEnrollmentPayload(payload + "x", signature, publicKey));
    }
}
