/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.signing.entity.StoredSigningKey;
import dev.chojo.ember.feature.storage.credential.CredentialCipher;
import dev.chojo.ember.feature.storage.credential.CredentialCipherException;
import dev.chojo.ember.feature.storage.credential.EncryptedBlob;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;

/**
 * Wraps signing private keys for storage and unwraps them again, with AES-256-GCM.
 *
 * <p>The wrapping key is derived from the installation's at-rest secret, the one {@link CredentialCipher}
 * seals mailbox passwords and federation keys with ({@code storage.credentialEncryptionKey}, else the
 * generated key file). There is no configuration value of its own, so the key file belongs in every
 * backup: without it no signing key opens again.
 *
 * <p>The derivation is {@link CredentialCipher#derivedFrom(String, String)}, {@code SHA-256(purpose, secret)},
 * the construction the federation key transfer already uses. The at-rest secret is 32 uniformly random
 * bytes, so one hash under a purpose of its own is a sound key derivation (the extract step of HKDF adds
 * nothing for a key that is already uniform), and the purpose keeps the wrapping key apart from the
 * at-rest key itself: a value sealed by one never opens with the other.
 *
 * <p>A wrapped key is one version byte ({@value #VERSION}), the 12-byte IV and the GCM ciphertext with its
 * tag. The ciphertext holds the length of the key algorithm name, the name in ASCII and the PKCS#8
 * encoding, so the algorithm is authenticated along with the key and unwrapping builds the same key
 * type. Any other version byte is refused, which leaves room for a later format beside this one.
 *
 * <p>The at-rest secret counts as unresolvable when the key file can be neither read nor created, when
 * it is blank (an empty key file), or when it is not base64 of 32 bytes. Each refuses construction, so
 * signing cannot start without a usable secret.
 */
@Singleton
public class SigningKeyWrap {
    /** The first byte of every key wrapped in the current format. */
    public static final byte VERSION = 1;

    private static final String PURPOSE = "ember-signing-key-wrap-v1";
    private static final int IV_BYTES = 12;
    private static final int HEADER_BYTES = 1 + IV_BYTES;
    private static final int MAX_ALGORITHM_NAME = 255;

    private final CredentialCipher cipher;

    /** Wraps under the installation's at-rest secret. */
    @Inject
    public SigningKeyWrap(Storage storageConfig) {
        this(resolve(storageConfig));
    }

    /**
     * Wraps under a key derived from this secret.
     *
     * @param atRestSecret the at-rest secret, base64 of 32 bytes
     * @throws SigningKeyWrapException when the secret is blank or malformed
     */
    public SigningKeyWrap(String atRestSecret) {
        requireUsable(atRestSecret);
        this.cipher = CredentialCipher.derivedFrom(PURPOSE, atRestSecret);
    }

    /**
     * Wraps a private key for storage.
     *
     * @param key a key that exports as PKCS#8
     * @return the version byte, IV and ciphertext
     * @throws SigningKeyWrapException when the key does not export as PKCS#8
     */
    public byte[] wrap(PrivateKey key) {
        byte[] plaintext = plaintextOf(key);
        try {
            EncryptedBlob blob = cipher.encrypt(plaintext);
            return ByteBuffer.allocate(HEADER_BYTES + blob.ciphertext().length)
                    .put(VERSION)
                    .put(blob.iv())
                    .put(blob.ciphertext())
                    .array();
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    /**
     * Unwraps a stored key and reads its certificate, ready to sign with.
     *
     * @param stored the key as it is stored
     * @return its private key and certificate
     * @throws SigningKeyWrapException as {@link #unwrap(byte[])} does
     */
    public SigningCertificates.Issued open(StoredSigningKey stored) {
        return new SigningCertificates.Issued(
                unwrap(stored.wrappedPrivateKey()), SigningCertificates.certificateOf(stored.certificate()));
    }

    /**
     * Unwraps a key written by {@link #wrap(PrivateKey)}.
     *
     * @param wrapped the stored bytes
     * @return the private key, of the algorithm it was wrapped with
     * @throws SigningKeyWrapException when the format is unknown, the bytes were altered or the key was
     *                                 wrapped under another secret
     */
    public PrivateKey unwrap(byte[] wrapped) {
        if (wrapped.length <= HEADER_BYTES) throw new SigningKeyWrapException("The wrapped signing key is too short");
        if (wrapped[0] != VERSION) {
            throw new SigningKeyWrapException("The wrapped signing key has an unknown format version " + wrapped[0]);
        }
        byte[] iv = Arrays.copyOfRange(wrapped, 1, HEADER_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(wrapped, HEADER_BYTES, wrapped.length);
        byte[] plaintext = open(new EncryptedBlob(iv, ciphertext));
        try {
            return keyOf(plaintext);
        } finally {
            Arrays.fill(plaintext, (byte) 0);
        }
    }

    private byte[] open(EncryptedBlob blob) {
        try {
            return cipher.decrypt(blob);
        } catch (CredentialCipherException e) {
            throw new SigningKeyWrapException(
                    "The signing key does not open: it was wrapped under another encryption key or has been altered",
                    e);
        }
    }

    private static byte[] plaintextOf(PrivateKey key) {
        byte[] encoded = key.getEncoded();
        if (encoded == null || !"PKCS#8".equals(key.getFormat())) {
            throw new SigningKeyWrapException("The signing key does not export as PKCS#8");
        }
        byte[] algorithm = key.getAlgorithm().getBytes(StandardCharsets.US_ASCII);
        if (algorithm.length == 0 || algorithm.length > MAX_ALGORITHM_NAME) {
            throw new SigningKeyWrapException("The signing key has no usable algorithm name");
        }
        try {
            return ByteBuffer.allocate(1 + algorithm.length + encoded.length)
                    .put((byte) algorithm.length)
                    .put(algorithm)
                    .put(encoded)
                    .array();
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static PrivateKey keyOf(byte[] plaintext) {
        int nameLength = Byte.toUnsignedInt(plaintext[0]);
        if (nameLength == 0 || plaintext.length <= 1 + nameLength) {
            throw new SigningKeyWrapException("The unwrapped signing key is malformed");
        }
        String algorithm = new String(plaintext, 1, nameLength, StandardCharsets.US_ASCII);
        byte[] encoded = Arrays.copyOfRange(plaintext, 1 + nameLength, plaintext.length);
        try {
            return KeyFactory.getInstance(algorithm).generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new SigningKeyWrapException("The unwrapped signing key cannot be read as " + algorithm, e);
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    private static String resolve(Storage storageConfig) {
        try {
            return CredentialCipher.atRestKey(storageConfig);
        } catch (RuntimeException e) {
            throw new SigningKeyWrapException(
                    "Signing cannot start: the at-rest encryption key cannot be read or created", e);
        }
    }

    private static void requireUsable(String atRestSecret) {
        boolean configured;
        try {
            configured = new CredentialCipher(atRestSecret).isConfigured();
        } catch (CredentialCipherException e) {
            throw new SigningKeyWrapException("Signing cannot start: " + e.getMessage(), e);
        }
        if (!configured) {
            throw new SigningKeyWrapException(
                    "Signing cannot start: the at-rest encryption key is empty; set storage.credentialEncryptionKey"
                            + " or restore the key file");
        }
    }
}
