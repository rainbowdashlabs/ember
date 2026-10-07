/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.CredentialCipherException;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Signing keys wrapped under a key derived from the at-rest secret: they come back as the same key of the
 * same algorithm, and only under the same secret, unaltered and never through the at-rest key itself.
 */
class SigningKeyWrapTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(filled((byte) 3));
    private static final String OTHER_SECRET = Base64.getEncoder().encodeToString(filled((byte) 9));

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static PrivateKey generate(String algorithm) throws Exception {
        return KeyPairGenerator.getInstance(algorithm).generateKeyPair().getPrivate();
    }

    @Test
    void anRsaKeyComesBackAsTheSameKey() throws Exception {
        var wrap = new SigningKeyWrap(SECRET);
        PrivateKey key = generate("RSA");

        PrivateKey unwrapped = wrap.unwrap(wrap.wrap(key));

        assertEquals("RSA", unwrapped.getAlgorithm());
        assertArrayEquals(key.getEncoded(), unwrapped.getEncoded());
    }

    @Test
    void anEcKeyComesBackAsTheSameKey() throws Exception {
        var wrap = new SigningKeyWrap(SECRET);
        PrivateKey key = generate("EC");

        PrivateKey unwrapped = wrap.unwrap(wrap.wrap(key));

        assertEquals("EC", unwrapped.getAlgorithm());
        assertArrayEquals(key.getEncoded(), unwrapped.getEncoded());
    }

    @Test
    void theWrappedKeyStartsWithTheVersionAndHidesTheKey() throws Exception {
        PrivateKey key = generate("EC");

        byte[] wrapped = new SigningKeyWrap(SECRET).wrap(key);

        assertEquals(SigningKeyWrap.VERSION, wrapped[0]);
        byte[] encoded = key.getEncoded();
        assertEquals(-1, indexOf(wrapped, Arrays.copyOfRange(encoded, encoded.length - 16, encoded.length)));
    }

    @Test
    void aKeyWrappedUnderOneSecretDoesNotOpenUnderAnother() throws Exception {
        byte[] wrapped = new SigningKeyWrap(SECRET).wrap(generate("EC"));

        assertThrows(SigningKeyWrapException.class, () -> new SigningKeyWrap(OTHER_SECRET).unwrap(wrapped));
    }

    @Test
    void everyFlippedByteIsRefused() throws Exception {
        var wrap = new SigningKeyWrap(SECRET);
        byte[] wrapped = wrap.wrap(generate("EC"));

        for (int i = 0; i < wrapped.length; i++) {
            byte[] altered = wrapped.clone();
            altered[i] ^= 0x01;
            assertThrows(SigningKeyWrapException.class, () -> wrap.unwrap(altered), "byte " + i);
        }
    }

    @Test
    void aTooShortValueIsRefused() {
        var wrap = new SigningKeyWrap(SECRET);

        assertThrows(SigningKeyWrapException.class, () -> wrap.unwrap(new byte[] {SigningKeyWrap.VERSION}));
    }

    @Test
    void aValueSealedWithTheAtRestKeyDoesNotOpenAsASigningKey() throws Exception {
        EncryptedBlob blob = new CredentialCipher(SECRET).encrypt(payload(generate("EC")));
        byte[] disguised = ByteBuffer.allocate(1 + blob.iv().length + blob.ciphertext().length)
                .put(SigningKeyWrap.VERSION)
                .put(blob.iv())
                .put(blob.ciphertext())
                .array();

        assertThrows(SigningKeyWrapException.class, () -> new SigningKeyWrap(SECRET).unwrap(disguised));
    }

    @Test
    void aWrappedSigningKeyDoesNotOpenWithTheAtRestKey() throws Exception {
        byte[] wrapped = new SigningKeyWrap(SECRET).wrap(generate("EC"));
        var blob =
                new EncryptedBlob(Arrays.copyOfRange(wrapped, 1, 13), Arrays.copyOfRange(wrapped, 13, wrapped.length));

        assertThrows(CredentialCipherException.class, () -> new CredentialCipher(SECRET).decrypt(blob));
    }

    @Test
    void theInjectedWrapUsesTheConfiguredSecret() throws Exception {
        Storage storage = mock(Storage.class);
        when(storage.credentialEncryptionKey()).thenReturn(SECRET);
        byte[] wrapped = new SigningKeyWrap(storage).wrap(generate("EC"));

        new SigningKeyWrap(SECRET).unwrap(wrapped);
    }

    @Test
    void everySpellingOfTheSameSecretOpensTheSameKeys() throws Exception {
        String unpadded = Base64.getEncoder().withoutPadding().encodeToString(filled((byte) 3));
        assertNotEquals(SECRET, unpadded, "two spellings of the same 32 bytes");
        PrivateKey key = generate("EC");

        byte[] wrapped = new SigningKeyWrap(SECRET).wrap(key);

        assertArrayEquals(
                key.getEncoded(), new SigningKeyWrap(unpadded).unwrap(wrapped).getEncoded());
        assertArrayEquals(
                key.getEncoded(),
                new SigningKeyWrap(SECRET)
                        .unwrap(new SigningKeyWrap(unpadded).wrap(key))
                        .getEncoded());
    }

    @Test
    void aBlankSecretRefusesToStart() {
        assertThrows(SigningKeyWrapException.class, () -> new SigningKeyWrap(""));
    }

    @Test
    void aMalformedSecretRefusesToStart() {
        assertThrows(SigningKeyWrapException.class, () -> new SigningKeyWrap("not base64!"));
        assertThrows(
                SigningKeyWrapException.class,
                () -> new SigningKeyWrap(Base64.getEncoder().encodeToString(new byte[16])));
    }

    private static byte[] payload(PrivateKey key) {
        byte[] algorithm = key.getAlgorithm().getBytes(StandardCharsets.US_ASCII);
        return ByteBuffer.allocate(1 + algorithm.length + key.getEncoded().length)
                .put((byte) algorithm.length)
                .put(algorithm)
                .put(key.getEncoded())
                .array();
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) return i;
        }
        return -1;
    }
}
