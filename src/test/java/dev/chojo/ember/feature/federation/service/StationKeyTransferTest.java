/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.service;

import dev.chojo.ember.feature.federation.repository.StationKeyRepository;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.CredentialCipherException;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.TestStationKeys;
import org.junit.jupiter.api.Test;

import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The federation key of a transferred station: it leaves sealed with the transfer token, never in
 * plaintext and never in the source instance's own encryption, and the destination stores it under
 * its own key so the station goes on signing as the one its partners know.
 */
class StationKeyTransferTest extends RepositoryTestBase {
    private static final String TOKEN = "transfer-token-" + UUID.randomUUID();

    private int newStation() {
        return stationRepo.create("KeyTransfer" + UUID.randomUUID()).id();
    }

    @Test
    void theDestinationSignsWithTheSameKeyAfterTheTransfer() {
        var source = TestStationKeys.store();
        int sourceStation = newStation();
        String publicKey = source.ensurePublicKey(sourceStation);
        String sealed =
                new StationKeyTransfer(source).seal(sourceStation, TOKEN).orElseThrow();

        var destination = new StationKeyStore(
                new StationKeyRepository(),
                new CredentialCipher(
                        Base64.getEncoder().encodeToString("d".repeat(32).getBytes())));
        int destinationStation = newStation();
        boolean adopted = new StationKeyTransfer(destination)
                .adopt(destinationStation, Map.of(StationKeyTransfer.FIELD, sealed), Map.of(), TOKEN);

        assertTrue(adopted);
        assertEquals(publicKey, destination.ensurePublicKey(destinationStation));
    }

    @Test
    void theSealedKeyIsNeitherPlaintextNorTheStoredValue() {
        var store = TestStationKeys.store();
        int stationId = newStation();
        store.ensurePublicKey(stationId);
        String plaintext = Base64.getEncoder()
                .encodeToString(store.privateKey(stationId).orElseThrow().getEncoded());

        String sealed = new StationKeyTransfer(store).seal(stationId, TOKEN).orElseThrow();

        assertFalse(sealed.contains(plaintext));
        assertFalse(sealed.equals(new StationKeyRepository().find(stationId).orElseThrow()));
    }

    @Test
    void anotherTokenCannotOpenIt() {
        var store = TestStationKeys.store();
        int stationId = newStation();
        store.ensurePublicKey(stationId);
        String sealed = new StationKeyTransfer(store).seal(stationId, TOKEN).orElseThrow();

        assertThrows(CredentialCipherException.class, () -> TestStationKeys.transfer()
                .adopt(newStation(), Map.of(StationKeyTransfer.FIELD, sealed), Map.of(), "another token"));
    }

    @Test
    void aStationWithoutAKeyExportsNone() {
        assertTrue(TestStationKeys.transfer().seal(newStation(), TOKEN).isEmpty());
    }

    @Test
    void aPlaintextKeyColumnFromAnOlderExportIsAdoptedAndEncrypted() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var key = generator.generateKeyPair().getPrivate();
        Map<String, Object> station = new HashMap<>();
        station.put("federation_private_key", Base64.getEncoder().encodeToString(key.getEncoded()));
        int stationId = newStation();

        assertTrue(TestStationKeys.transfer().adopt(stationId, Map.of(), station, TOKEN));

        assertTrue(CredentialCipher.isSealed(
                new StationKeyRepository().find(stationId).orElseThrow()));
        assertEquals(StationKeyStore.publicKeyOf(key), TestStationKeys.store().ensurePublicKey(stationId));
    }

    @Test
    void aPageWithoutAKeyLeavesTheStationWithout() {
        int stationId = newStation();

        assertFalse(TestStationKeys.transfer().adopt(stationId, Map.of(), Map.of(), TOKEN));
        assertTrue(new StationKeyRepository().find(stationId).isEmpty());
    }
}
