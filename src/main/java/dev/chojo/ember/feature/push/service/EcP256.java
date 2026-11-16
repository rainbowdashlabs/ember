/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.ec.CustomNamedCurves;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;

/**
 * Keys on the P-256 curve in the shapes Web Push writes them: a public key as the 65 bytes of an
 * uncompressed point, a private key as its 32-byte scalar.
 *
 * <p>The JDK holds and uses the keys; BouncyCastle reads a point off the wire, which also refuses a
 * point that is not on the curve before any key agreement is attempted with it.
 */
public final class EcP256 {
    /** The length of an uncompressed public key: the marker byte and two 32-byte coordinates. */
    public static final int PUBLIC_KEY_BYTES = 65;

    private static final int COORDINATE_BYTES = 32;
    private static final byte UNCOMPRESSED = 0x04;
    private static final X9ECParameters CURVE = CustomNamedCurves.getByName("secp256r1");
    private static final ECParameterSpec PARAMETERS = parameters();

    private EcP256() {}

    /** @return a fresh key pair on the curve */
    public static KeyPair generate() {
        try {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JDK offers no P-256 keys", e);
        }
    }

    /**
     * Reads a public key sent as an uncompressed point.
     *
     * @param uncompressed the 65 bytes
     * @return the key
     * @throws IllegalArgumentException when the bytes are not a point on the curve
     */
    public static ECPublicKey publicKeyOf(byte[] uncompressed) {
        if (uncompressed.length != PUBLIC_KEY_BYTES || uncompressed[0] != UNCOMPRESSED) {
            throw new IllegalArgumentException("A P-256 public key is an uncompressed point of 65 bytes");
        }
        org.bouncycastle.math.ec.ECPoint point;
        try {
            point = CURVE.getCurve().decodePoint(uncompressed).normalize();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("The public key is not a point on P-256", e);
        }
        if (!point.isValid()) throw new IllegalArgumentException("The public key is not a point on P-256");
        var spec = new ECPublicKeySpec(
                new ECPoint(
                        point.getAffineXCoord().toBigInteger(),
                        point.getAffineYCoord().toBigInteger()),
                PARAMETERS);
        try {
            return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("The public key is not a point on P-256", e);
        }
    }

    /**
     * Builds a private key from its scalar.
     *
     * @param scalar the 32 bytes
     * @return the key
     */
    public static ECPrivateKey privateKeyOf(byte[] scalar) {
        try {
            return (ECPrivateKey) KeyFactory.getInstance("EC")
                    .generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), PARAMETERS));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Not a P-256 private key", e);
        }
    }

    /**
     * Writes a public key as an uncompressed point.
     *
     * @param key the key
     * @return the 65 bytes
     */
    public static byte[] uncompressed(ECPublicKey key) {
        var out = new byte[PUBLIC_KEY_BYTES];
        out[0] = UNCOMPRESSED;
        writeCoordinate(key.getW().getAffineX(), out, 1);
        writeCoordinate(key.getW().getAffineY(), out, 1 + COORDINATE_BYTES);
        return out;
    }

    private static void writeCoordinate(BigInteger value, byte[] out, int offset) {
        byte[] bytes = value.toByteArray();
        int start = Math.max(0, bytes.length - COORDINATE_BYTES);
        int copied = bytes.length - start;
        System.arraycopy(bytes, start, out, offset + COORDINATE_BYTES - copied, copied);
    }

    private static ECParameterSpec parameters() {
        try {
            var parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("The JDK offers no P-256 keys", e);
        }
    }
}
