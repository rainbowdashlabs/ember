/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.interfaces.ECPrivateKey;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * What a push carries is encrypted the way browsers decrypt it.
 *
 * <p>The worked example of RFC 8291 section 5 is the proof: its keys, secret and salt must give its
 * body byte for byte, which pins the key agreement, both derivations, the padding and the header.
 */
class PushPayloadCipherTest {
    private static final Base64.Decoder URL = Base64.getUrlDecoder();

    private static final byte[] PLAINTEXT =
            "When I grow up, I want to be a watermelon".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AS_PRIVATE = URL.decode("yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw");
    private static final byte[] AS_PUBLIC =
            URL.decode("BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8");
    private static final byte[] UA_PUBLIC =
            URL.decode("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4");
    private static final byte[] AUTH_SECRET = URL.decode("BTBZMqHH6r4Tts7J_aSIgg");
    private static final byte[] SALT = URL.decode("DGv6ra1nlYgDCS1FRnbzlw");
    private static final byte[] BODY = URL.decode(
            "DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A_yl95bQpu6cVPTpK4Mqgkf1CXztLVBSt2Ks3oZwbuwXPXLWyouBWLVWGNWQexSgSxsj_Qulcy4a-fN");

    @Test
    void theWorkedExampleOfTheStandardComesOutByteForByte() {
        var asKeys = new KeyPair(EcP256.publicKeyOf(AS_PUBLIC), EcP256.privateKeyOf(AS_PRIVATE));

        byte[] body = PushPayloadCipher.encrypt(PLAINTEXT, UA_PUBLIC, AUTH_SECRET, asKeys, SALT);

        assertArrayEquals(BODY, body);
    }

    /** Every message gets its own key and salt, so two of the same text never look alike. */
    @Test
    void theSameMessageTwiceIsEncryptedDifferently() {
        byte[] first = PushPayloadCipher.encrypt(PLAINTEXT, UA_PUBLIC, AUTH_SECRET);
        byte[] second = PushPayloadCipher.encrypt(PLAINTEXT, UA_PUBLIC, AUTH_SECRET);

        assertEquals(BODY.length, first.length);
        assertFalse(Arrays.equals(first, second));
    }

    @Test
    void aSubscriptionWithUnusableKeysIsRefused() {
        assertThrows(
                IllegalArgumentException.class, () -> PushPayloadCipher.encrypt(PLAINTEXT, UA_PUBLIC, new byte[15]));
        var offCurve = UA_PUBLIC.clone();
        offCurve[64] ^= 1;
        assertThrows(IllegalArgumentException.class, () -> PushPayloadCipher.encrypt(PLAINTEXT, offCurve, AUTH_SECRET));
        assertThrows(
                IllegalArgumentException.class,
                () -> PushPayloadCipher.encrypt(PLAINTEXT, Arrays.copyOf(UA_PUBLIC, 33), AUTH_SECRET));
    }

    /** The longest message that fits is taken, one byte more is refused, so no push service answers 413. */
    @Test
    void aMessageLongerThanAPushCarriesIsRefused() {
        byte[] body =
                PushPayloadCipher.encrypt(new byte[PushPayloadCipher.MAX_PLAINTEXT_BYTES], UA_PUBLIC, AUTH_SECRET);
        assertEquals(4096, body.length);
        assertThrows(
                IllegalArgumentException.class,
                () -> PushPayloadCipher.encrypt(
                        new byte[PushPayloadCipher.MAX_PLAINTEXT_BYTES + 1], UA_PUBLIC, AUTH_SECRET));
    }

    /** A key read from its scalar and written back is the same key, which the stored key pair relies on. */
    @Test
    void aPrivateKeyReadsBackFromItsScalar() {
        ECPrivateKey key = EcP256.privateKeyOf(AS_PRIVATE);

        assertEquals(new java.math.BigInteger(1, AS_PRIVATE), key.getS());
        assertArrayEquals(AS_PUBLIC, EcP256.uncompressed(EcP256.publicKeyOf(AS_PUBLIC)));
    }
}
