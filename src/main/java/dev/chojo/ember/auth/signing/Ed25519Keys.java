/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth.signing;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

/**
 * Converts Ed25519 public keys between the Java form and the raw 32 bytes that travel on the wire.
 *
 * <p>The raw form is the RFC 8032 point encoding. Java's X.509 encoding of an Ed25519 key is that
 * same encoding behind a fixed 12-byte SubjectPublicKeyInfo header naming the algorithm, so
 * converting is a matter of adding or removing the header; the security provider does the point
 * arithmetic.
 */
public final class Ed25519Keys {
    /** The number of bytes in a raw Ed25519 public key. */
    public static final int RAW_LENGTH = 32;

    private static final byte[] X509_PREFIX = {0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00};
    private static final String ALGORITHM = "Ed25519";

    private Ed25519Keys() {}

    /**
     * The raw 32-byte form of a key.
     *
     * @param key an Ed25519 public key
     * @return the RFC 8032 encoding
     * @throws IllegalArgumentException when the key is not an Ed25519 key
     */
    public static byte[] raw(PublicKey key) {
        byte[] encoded = key.getEncoded();
        if (encoded.length != X509_PREFIX.length + RAW_LENGTH
                || !Arrays.equals(encoded, 0, X509_PREFIX.length, X509_PREFIX, 0, X509_PREFIX.length)) {
            throw new IllegalArgumentException("Expected an Ed25519 public key, got " + key.getAlgorithm());
        }
        return Arrays.copyOfRange(encoded, X509_PREFIX.length, encoded.length);
    }

    /**
     * The key a raw 32-byte form stands for.
     *
     * @param raw the RFC 8032 encoding
     * @return the public key
     * @throws IllegalArgumentException when the bytes are not a key
     */
    public static PublicKey fromRaw(byte[] raw) {
        if (raw.length != RAW_LENGTH) {
            throw new IllegalArgumentException(
                    "Ed25519 public key must be " + RAW_LENGTH + " bytes, got " + raw.length);
        }
        byte[] encoded = Arrays.copyOf(X509_PREFIX, X509_PREFIX.length + RAW_LENGTH);
        System.arraycopy(raw, 0, encoded, X509_PREFIX.length, RAW_LENGTH);
        try {
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(encoded));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not an Ed25519 public key", e);
        }
    }
}
