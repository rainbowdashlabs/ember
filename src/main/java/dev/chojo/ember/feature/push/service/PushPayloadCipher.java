/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.generators.HKDFBytesGenerator;
import org.bouncycastle.crypto.params.HKDFParameters;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Encrypts what a push carries for the one browser that subscribed, as RFC 8291 lays it down.
 *
 * <p>Every message gets a key pair of its own and a fresh salt. From the browser's public key and the
 * secret it handed over at subscription, both sides derive the same content key, so only that browser
 * reads the message: the push service between them sees its length and nothing else. The result is a
 * single {@code aes128gcm} record (RFC 8188), whose header carries the salt and this message's public
 * key so the browser can derive the key again.
 */
public final class PushPayloadCipher {
    /** The record size written into the header: a push is one record, and push services take 4096 bytes. */
    static final int RECORD_SIZE = 4096;

    /**
     * The longest message that still fits: what push services take, less the header (salt, record
     * size, key length, this message's public key) and the padding delimiter and tag of the record.
     */
    public static final int MAX_PLAINTEXT_BYTES = RECORD_SIZE - (16 + 4 + 1 + EcP256.PUBLIC_KEY_BYTES) - 1 - 16;

    private static final int AUTH_SECRET_BYTES = 16;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 16;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final byte LAST_RECORD = 0x02;
    private static final byte[] KEY_INFO = "WebPush: info\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CEK_INFO = "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] NONCE_INFO = "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private PushPayloadCipher() {}

    /**
     * Encrypts a message for one subscription.
     *
     * @param plaintext   the message
     * @param uaPublicKey the browser's public key, the {@code p256dh} of its subscription
     * @param authSecret  the browser's 16-byte secret, the {@code auth} of its subscription
     * @return the request body to post to the subscription's endpoint
     * @throws IllegalArgumentException when the subscription's keys are not usable or the message is
     *     longer than {@link #MAX_PLAINTEXT_BYTES}
     */
    public static byte[] encrypt(byte[] plaintext, byte[] uaPublicKey, byte[] authSecret) {
        var salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return encrypt(plaintext, uaPublicKey, authSecret, EcP256.generate(), salt);
    }

    /**
     * Encrypts with a given key pair and salt, which only the tests choose: reusing either between two
     * messages would let anybody who sees both learn about their contents.
     */
    static byte[] encrypt(byte[] plaintext, byte[] uaPublicKey, byte[] authSecret, KeyPair asKeys, byte[] salt) {
        if (plaintext.length > MAX_PLAINTEXT_BYTES) {
            throw new IllegalArgumentException("A push carries at most " + MAX_PLAINTEXT_BYTES + " bytes");
        }
        if (authSecret.length != AUTH_SECRET_BYTES) {
            throw new IllegalArgumentException("A subscription's auth secret is 16 bytes");
        }
        ECPublicKey uaKey = EcP256.publicKeyOf(uaPublicKey);
        byte[] asPublicKey = EcP256.uncompressed((ECPublicKey) asKeys.getPublic());
        byte[] sharedSecret = agree(asKeys, uaKey);
        try {
            byte[] keyInfo = concat(KEY_INFO, uaPublicKey, asPublicKey);
            byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);
            byte[] contentKey = hkdf(salt, ikm, CEK_INFO, KEY_BYTES);
            byte[] nonce = hkdf(salt, ikm, NONCE_INFO, NONCE_BYTES);
            byte[] record = seal(contentKey, nonce, concat(plaintext, new byte[] {LAST_RECORD}));
            return ByteBuffer.allocate(SALT_BYTES + 4 + 1 + asPublicKey.length + record.length)
                    .put(salt)
                    .putInt(RECORD_SIZE)
                    .put((byte) asPublicKey.length)
                    .put(asPublicKey)
                    .put(record)
                    .array();
        } finally {
            Arrays.fill(sharedSecret, (byte) 0);
        }
    }

    private static byte[] agree(KeyPair asKeys, ECPublicKey uaKey) {
        try {
            var agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(asKeys.getPrivate());
            agreement.doPhase(uaKey, true);
            return agreement.generateSecret();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("No key could be agreed with the subscription's public key", e);
        }
    }

    private static byte[] seal(byte[] key, byte[] nonce, byte[] padded) {
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
            return cipher.doFinal(padded);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JDK offers no AES-GCM", e);
        }
    }

    private static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) {
        var generator = new HKDFBytesGenerator(new SHA256Digest());
        generator.init(new HKDFParameters(ikm, salt, info));
        var out = new byte[length];
        generator.generateBytes(out, 0, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        var out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }
}
