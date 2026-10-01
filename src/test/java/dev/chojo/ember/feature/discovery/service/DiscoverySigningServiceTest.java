/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscoverySigningServiceTest {

    @TempDir
    static Path tempDir;

    private static DiscoveryKeyService keyService;
    private static DiscoverySigningService signingService;

    @BeforeAll
    static void init() {
        keyService = new DiscoveryKeyService(tempDir.resolve("discovery"));
        signingService = new DiscoverySigningService(keyService);
    }

    @Test
    void keysAreKeptInTheGivenDirectory() {
        assertTrue(Files.isRegularFile(tempDir.resolve("discovery").resolve("private.key")));
        assertTrue(Files.isRegularFile(tempDir.resolve("discovery").resolve("public.key")));
    }

    @Test
    void anExistingKeypairIsLoadedRatherThanReplaced() {
        var reloaded = new DiscoveryKeyService(tempDir.resolve("discovery"));
        assertEquals(keyService.publicKeyBase64(), reloaded.publicKeyBase64());
        assertEquals(keyService.instanceId(), reloaded.instanceId());
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

        var gen = KeyPairGenerator.getInstance("Ed25519");
        KeyPair other = gen.generateKeyPair();
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
