/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.repository.StationKeyRepository;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A station's federation key at rest: encrypted when written, still readable when written before
 * encryption, converted once at start-up, and still producing signatures its partners accept.
 */
class StationKeyStoreTest extends RepositoryTestBase {
    private final StationKeyRepository repository = new StationKeyRepository();

    private static PrivateKey freshKey() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair().getPrivate();
    }

    private static String plaintext(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private int newStation() {
        return stationRepo.create("KeyStore" + UUID.randomUUID()).id();
    }

    @Test
    void aGeneratedKeyIsStoredEncryptedAndKeptOnLaterCalls() {
        var store = TestStationKeys.store();
        int stationId = newStation();

        String publicKey = store.ensurePublicKey(stationId);
        String stored = repository.find(stationId).orElseThrow();

        assertTrue(CredentialCipher.isSealed(stored));
        assertFalse(stored.contains(plaintext(store.privateKey(stationId).orElseThrow())));
        assertEquals(publicKey, store.ensurePublicKey(stationId));
        assertEquals(publicKey, TestStationKeys.store().ensurePublicKey(stationId));
    }

    @Test
    void aPlaintextKeyFromBeforeEncryptionIsStillReadable() throws Exception {
        int stationId = newStation();
        var key = freshKey();
        repository.replace(stationId, plaintext(key));

        assertArrayEquals(
                key.getEncoded(),
                TestStationKeys.store().privateKey(stationId).orElseThrow().getEncoded());
    }

    @Test
    void theStartupConversionEncryptsPlaintextKeysOnceAndKeepsThemUsable() throws Exception {
        int stationId = newStation();
        var key = freshKey();
        repository.replace(stationId, plaintext(key));
        var store = TestStationKeys.store();

        assertTrue(store.sealLegacyKeys() >= 1);
        String stored = repository.find(stationId).orElseThrow();
        assertTrue(CredentialCipher.isSealed(stored));
        assertFalse(stored.contains(plaintext(key)));
        assertArrayEquals(
                key.getEncoded(),
                TestStationKeys.store().privateKey(stationId).orElseThrow().getEncoded());

        assertEquals(0, store.sealLegacyKeys());
        assertEquals(stored, repository.find(stationId).orElseThrow());
    }

    @Test
    void anUnreadableValueIsLeftAloneByTheConversion() {
        int stationId = newStation();
        repository.replace(stationId, "not a key");

        TestStationKeys.store().sealLegacyKeys();

        assertEquals("not a key", repository.find(stationId).orElseThrow());
    }

    @Test
    void aKeyEncryptedUnderAnotherInstanceKeyCannotBeRead() {
        int stationId = newStation();
        var other = new CredentialCipher(
                Base64.getEncoder().encodeToString("x".repeat(32).getBytes()));
        new StationKeyStore(repository, other).ensurePublicKey(stationId);

        assertThrows(RuntimeException.class, () -> TestStationKeys.store().privateKey(stationId));
    }

    @Test
    void adoptingAKeyReplacesTheOldOne() throws Exception {
        var store = TestStationKeys.store();
        int stationId = newStation();
        String before = store.ensurePublicKey(stationId);
        var adopted = freshKey();

        store.adopt(stationId, adopted);

        assertNotEquals(before, store.ensurePublicKey(stationId));
        assertArrayEquals(
                adopted.getEncoded(),
                TestStationKeys.store().privateKey(stationId).orElseThrow().getEncoded());
    }

    @Test
    void aSignatureMadeWithTheStoredKeyVerifiesAgainstItsPublicKey() {
        var signing = new FederationSigningService();
        var signer = new StationSigner(TestStationKeys.store(), signing);
        int stationId = newStation();
        String publicKey = signer.ensurePublicKey(stationId);
        UUID recipient = UUID.randomUUID();
        String nonce = UUID.randomUUID().toString();
        Instant timestamp = Instant.now();

        String signature = signer.signRequest(
                stationId, "POST", "/api/v1/remote/x?a=1", recipient, nonce, "{}", timestamp.toString());

        assertTrue(signing.verify(
                "POST",
                "/api/v1/remote/x?a=1",
                recipient,
                nonce,
                "{}",
                signature,
                signing.decodePublicKey(publicKey),
                timestamp));
    }

    @Test
    void aStationWithoutAKeyCannotSign() {
        var signer = TestStationKeys.signer();
        int stationId = newStation();

        assertFalse(signer.canSign(stationId));
        assertThrows(IllegalStateException.class, () -> signer.signEnrollment(stationId, "payload"));
    }
}
