/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import dev.chojo.ember.conf.file.elements.Storage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encrypt / decrypt for station-supplied remote-backend credentials. The key is
 * read once from {@code storage.credentialEncryptionKey} (base64-encoded 32 bytes) and held
 * in memory for the lifetime of the process; plaintext never leaves the method body.
 *
 * <p>Each {@link #encrypt(byte[])} call generates a fresh 12-byte IV and appends the
 * 16-byte GCM tag to the ciphertext (Java's GCM cipher does this for us). The IV is returned
 * alongside the ciphertext as an {@link EncryptedBlob} and must be passed back on decrypt;
 * never reused for two encryptions with the same key.
 *
 * <p>{@link #encrypt(byte[])} and {@link #decrypt(EncryptedBlob)} throw
 * {@link CredentialCipherException} on any cipher failure, including a missing key — that
 * way every caller funnels through one error path instead of catching seven separate JCE
 * exceptions.
 */
@Singleton
public class CredentialCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    @Inject
    public CredentialCipher(Storage storageConfig) {
        this(storageConfig.credentialEncryptionKey());
    }

    /** Visible for tests that supply the key directly without going through {@link Storage}. */
    public CredentialCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            this.key = null;
            return;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            throw new CredentialCipherException("credentialEncryptionKey is not valid base64", e);
        }
        if (decoded.length != KEY_BYTES) {
            throw new CredentialCipherException(
                    "credentialEncryptionKey must decode to " + KEY_BYTES + " bytes, got " + decoded.length);
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    /** Whether a key is configured; callers that depend on encryption check this on startup. */
    public boolean isConfigured() {
        return key != null;
    }

    /**
     * Encrypts {@code plaintext} with a freshly-generated IV. The IV and ciphertext+tag are
     * returned together as an {@link EncryptedBlob}.
     */
    public EncryptedBlob encrypt(byte[] plaintext) {
        requireKey();
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedBlob(iv, cipher.doFinal(plaintext));
        } catch (Exception e) {
            throw new CredentialCipherException("Encryption failed", e);
        }
    }

    /** Convenience overload for UTF-8 string input. */
    public EncryptedBlob encrypt(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /** Decrypts an {@link EncryptedBlob} produced by {@link #encrypt(byte[])}. */
    public byte[] decrypt(EncryptedBlob blob) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, blob.iv()));
            return cipher.doFinal(blob.ciphertext());
        } catch (Exception e) {
            throw new CredentialCipherException("Decryption failed", e);
        }
    }

    /** Convenience overload that returns the plaintext as a UTF-8 string. */
    public String decryptToString(EncryptedBlob blob) {
        return new String(decrypt(blob), StandardCharsets.UTF_8);
    }

    private void requireKey() {
        if (key == null) {
            throw new CredentialCipherException(
                    "storage.credentialEncryptionKey is not configured; cannot encrypt or decrypt remote backend credentials");
        }
    }
}
