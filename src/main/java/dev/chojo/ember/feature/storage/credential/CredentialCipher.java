/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.util.RandomTokens;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM for the secrets Ember keeps at rest: remote-backend credentials, mailbox passwords and
 * federation signing keys. The key is {@code storage.credentialEncryptionKey} (base64, 32 bytes) when
 * configured, else the one generated into {@link EncryptionKeyFile#DEFAULT_PATH}. Every encryption
 * takes a fresh 12-byte IV, and every failure, a missing key included, is a {@link CredentialCipherException}.
 *
 * <p>A secret in a text column goes through {@link #seal(String)}, whose {@value #SEALED_PREFIX} prefix
 * tells it from a plaintext value written before encryption existed.
 */
@Singleton
public class CredentialCipher {
    /** Marks a text value written by {@link #seal(String)}, and names the format version. */
    public static final String SEALED_PREFIX = "enc:v1:";

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecretKeySpec key;

    @Inject
    public CredentialCipher(Storage storageConfig) {
        this(new EncryptionKeyFile(EncryptionKeyFile.DEFAULT_PATH).keyFor(storageConfig.credentialEncryptionKey()));
    }

    /**
     * A cipher keyed by {@code SHA-256(purpose, secret)}, so a value can travel sealed between two ends
     * that share a random secret without either revealing its own at-rest key. The purpose keeps keys
     * for unrelated uses of one secret apart.
     */
    public static CredentialCipher derivedFrom(String purpose, String secret) {
        return new CredentialCipher(Base64.getEncoder().encodeToString(Sha256.bytes(purpose + '\0' + secret)));
    }

    /** Whether a text value, possibly null, was written by {@link #seal(String)}. */
    public static boolean isSealed(String value) {
        return value != null && value.startsWith(SEALED_PREFIX);
    }

    /** @param base64Key the key; blank for a cipher that refuses every call */
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

    public boolean isConfigured() {
        return key != null;
    }

    public EncryptedBlob encrypt(byte[] plaintext) {
        requireKey();
        byte[] iv = RandomTokens.bytes(IV_BYTES);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new EncryptedBlob(iv, cipher.doFinal(plaintext));
        } catch (Exception e) {
            throw new CredentialCipherException("Encryption failed", e);
        }
    }

    public EncryptedBlob encrypt(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

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

    public String decryptToString(EncryptedBlob blob) {
        return new String(decrypt(blob), StandardCharsets.UTF_8);
    }

    /** Encrypts a text secret into {@value #SEALED_PREFIX} followed by the base64 of IV and ciphertext. */
    public String seal(String plaintext) {
        var blob = encrypt(plaintext);
        byte[] joined = new byte[blob.iv().length + blob.ciphertext().length];
        System.arraycopy(blob.iv(), 0, joined, 0, blob.iv().length);
        System.arraycopy(blob.ciphertext(), 0, joined, blob.iv().length, blob.ciphertext().length);
        return SEALED_PREFIX + Base64.getEncoder().encodeToString(joined);
    }

    /**
     * Decrypts a value written by {@link #seal(String)}.
     *
     * @throws CredentialCipherException when the value is not sealed, or not with this key
     */
    public String unseal(String sealed) {
        if (!isSealed(sealed)) {
            throw new CredentialCipherException("Value is not sealed");
        }
        byte[] joined;
        try {
            joined = Base64.getDecoder().decode(sealed.substring(SEALED_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new CredentialCipherException("Sealed value is not valid base64", e);
        }
        if (joined.length <= IV_BYTES) {
            throw new CredentialCipherException("Sealed value is too short");
        }
        byte[] iv = Arrays.copyOfRange(joined, 0, IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(joined, IV_BYTES, joined.length);
        return decryptToString(new EncryptedBlob(iv, ciphertext));
    }

    private void requireKey() {
        if (key == null) {
            throw new CredentialCipherException(
                    "storage.credentialEncryptionKey is not configured; cannot encrypt or decrypt remote backend credentials");
        }
    }
}
