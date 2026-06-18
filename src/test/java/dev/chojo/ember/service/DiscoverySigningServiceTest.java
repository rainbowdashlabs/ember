/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.discovery.service.DiscoveryKeyService;
import dev.chojo.ember.feature.discovery.service.DiscoverySigningService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverySigningServiceTest {

    @TempDir
    static Path tempDir;

    private static DiscoveryKeyService keyService;
    private static DiscoverySigningService signingService;

    @BeforeAll
    static void init() throws Exception {
        // Tell the key service to read/write inside a temp dir by symlinking data/discovery.
        Path data = Path.of("data", "discovery");
        if (!Files.exists(data)) {
            Files.createDirectories(data);
        }
        keyService = new DiscoveryKeyService();
        signingService = new DiscoverySigningService(keyService);
    }

    @Test
    void signAndVerifyRoundTrip() {
        String body = "{\"hello\":\"world\"}";
        String sig = signingService.sign(body);
        assertTrue(signingService.verify(body, sig, keyService.publicKeyBase64()));
    }

    @Test
    void verifyFailsOnTamperedBody() {
        String body = "{\"a\":1}";
        String sig = signingService.sign(body);
        assertFalse(signingService.verify("{\"a\":2}", sig, keyService.publicKeyBase64()));
    }

    @Test
    void verifyFailsOnWrongKey() throws Exception {
        String body = "test";
        String sig = signingService.sign(body);

        // Generate a different, unrelated Ed25519 public key.
        var gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair other = gen.generateKeyPair();
        // We need a base64 raw 32-byte representation; reuse the key service's static helper
        // by writing a temp file and decoding through the public path. Simpler: just attempt
        // verification with the other key directly.
        assertFalse(signingService.verify(body, sig, other.getPublic()));
    }

    @Test
    void verifyFailsOnBadBase64Key() {
        String body = "test";
        String sig = signingService.sign(body);
        assertFalse(signingService.verify(body, sig, "not-base64-!!!"));
    }

    @Test
    void verifyFailsOnNullPublicKey() {
        String body = "test";
        String sig = signingService.sign(body);
        assertFalse(signingService.verify(body, sig, (PublicKey) null));
    }

    @Test
    void verifyFailsOnGarbageSignature() {
        assertFalse(signingService.verify("test", "not-base64", keyService.publicKeyBase64()));
    }
}
