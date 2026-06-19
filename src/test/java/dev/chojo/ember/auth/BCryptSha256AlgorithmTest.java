/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BCryptSha256AlgorithmTest {

    private final BCryptSha256Algorithm algorithm = new BCryptSha256Algorithm();

    @Test
    void roundTripShortPassword() {
        var hashed = algorithm.hash("CorrectHorseBattery1");
        assertTrue(algorithm.verify("CorrectHorseBattery1", hashed));
        assertFalse(algorithm.verify("CorrectHorseBattery2", hashed));
    }

    @Test
    void longPassphraseRoundTripsCorrectly() {
        String passphrase = "a".repeat(200) + "Z!9";
        var hashed = algorithm.hash(passphrase);
        assertTrue(algorithm.verify(passphrase, hashed));
    }

    @Test
    void longPassphrasesSharing72ByteSuffixAreDistinct() {
        String common = "x".repeat(72);
        String a = common + "alpha";
        String b = common + "bravo";

        var hashedA = algorithm.hash(a);
        assertTrue(algorithm.verify(a, hashedA), "Verifying with the same passphrase must succeed");
        assertFalse(algorithm.verify(b, hashedA), "Two distinct passphrases sharing a 72-byte prefix must not collide");
    }

    @Test
    void algorithmNameIsBcryptSha256() {
        assertEquals("bcrypt-sha256", algorithm.name());
    }

    @Test
    void differentSaltsProduceDifferentHashes() {
        var a = algorithm.hash("samepassword12");
        var b = algorithm.hash("samepassword12");
        assertNotEquals(a.hash(), b.hash(), "Salt randomisation should yield distinct stored hashes");
        assertTrue(algorithm.verify("samepassword12", a));
        assertTrue(algorithm.verify("samepassword12", b));
    }
}
