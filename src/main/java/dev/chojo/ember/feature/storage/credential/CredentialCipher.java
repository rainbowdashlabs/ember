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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM encrypt / decrypt for the secrets Ember keeps at rest: station-supplied
 * remote-backend credentials, mailbox passwords and the stations' federation signing keys. The
 * key is {@code storage.credentialEncryptionKey} (base64-encoded 32 bytes) when the operator
 * configured one, and otherwise the key Ember generated into {@link EncryptionKeyFile#DEFAULT_PATH}
 * on its first start. It is read once and held in memory for the lifetime of the process;
 * plaintext never leaves the method body.
 *
 * <p>Each {@link #encrypt(byte[])} call generates a fresh 12-byte IV and appends the
 * 16-byte GCM tag to the ciphertext (Java's GCM cipher does this for us). The IV is returned
 * alongside the ciphertext as an {@link EncryptedBlob} and must be passed back on decrypt;
 * never reused for two encryptions with the same key.
 *
 * <p>A secret kept in a text column uses {@link #seal(String)} instead, which writes IV and
 * ciphertext as one string behind the {@value #SEALED_PREFIX} prefix. The prefix is what tells a
 * sealed value from a plaintext one written before encryption existed, so a reader can accept both
 * while old rows are being converted.
 *
 * <p>{@link #encrypt(byte[])} and {@link #decrypt(EncryptedBlob)} throw
 * {@link CredentialCipherException} on any cipher failure, including a missing key - that
 * way every caller funnels through one error path instead of catching seven separate JCE
 * exceptions.
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
    private final SecureRandom random = new SecureRandom();

    @Inject
    public CredentialCipher(Storage storageConfig) {
        this(new EncryptionKeyFile(EncryptionKeyFile.DEFAULT_PATH).keyFor(storageConfig.credentialEncryptionKey()));
    }

    /**
     * A cipher whose key is derived from a secret both ends of an exchange already share, so a value
     * can travel sealed without either end revealing its own at-rest key.
     *
     * <p>The secret has to carry enough entropy on its own, as a random token does. The purpose goes
     * into the derivation so the same secret yields unrelated keys for unrelated uses.
     *
     * @param purpose names what the key is for
     * @param secret  the shared secret
     * @return a cipher keyed by {@code SHA-256(purpose, secret)}
     */
    public static CredentialCipher derivedFrom(String purpose, String secret) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update(purpose.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(secret.getBytes(StandardCharsets.UTF_8));
            return new CredentialCipher(Base64.getEncoder().encodeToString(digest.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new CredentialCipherException("SHA-256 is not available", e);
        }
    }

    /**
     * Whether a text value was written by {@link #seal(String)}.
     *
     * @param value the stored value, may be {@code null}
     * @return true when the value carries the sealed prefix
     */
    public static boolean isSealed(String value) {
        return value != null && value.startsWith(SEALED_PREFIX);
    }

    /**
     * Visible for tests that supply the key directly without going through {@link Storage}.
     */
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

    /**
     * Whether a key is configured; callers that depend on encryption check this on startup.
     */
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

    /**
     * Convenience overload for UTF-8 string input.
     */
    public EncryptedBlob encrypt(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decrypts an {@link EncryptedBlob} produced by {@link #encrypt(byte[])}.
     */
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

    /**
     * Convenience overload that returns the plaintext as a UTF-8 string.
     */
    public String decryptToString(EncryptedBlob blob) {
        return new String(decrypt(blob), StandardCharsets.UTF_8);
    }

    /**
     * Encrypts a text secret into a single text value for a text column.
     *
     * @param plaintext the secret
     * @return {@value #SEALED_PREFIX} followed by the base64 of IV and ciphertext
     */
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
     * @param sealed the stored value
     * @return the secret
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
