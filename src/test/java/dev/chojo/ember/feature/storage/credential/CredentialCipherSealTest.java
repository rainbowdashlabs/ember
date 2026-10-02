/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.credential;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Secrets kept in a text column: sealed with a marker, opened again with the same key only, and told
 * apart from a plaintext value written before encryption existed.
 */
class CredentialCipherSealTest {
    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    private static final String OTHER_KEY = Base64.getEncoder().encodeToString(filled((byte) 7));

    private static byte[] filled(byte value) {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, value);
        return bytes;
    }

    @Test
    void aSealedValueOpensToWhatWasSealed() {
        var cipher = new CredentialCipher(KEY);

        String sealed = cipher.seal("MIIEvQ-private-key");

        assertTrue(CredentialCipher.isSealed(sealed));
        assertFalse(sealed.contains("MIIEvQ"));
        assertEquals("MIIEvQ-private-key", cipher.unseal(sealed));
    }

    @Test
    void sealingTheSameValueTwiceNeverGivesTheSameText() {
        var cipher = new CredentialCipher(KEY);

        assertNotEquals(cipher.seal("value"), cipher.seal("value"));
    }

    @Test
    void anotherKeyCannotOpenIt() {
        String sealed = new CredentialCipher(KEY).seal("value");

        assertThrows(CredentialCipherException.class, () -> new CredentialCipher(OTHER_KEY).unseal(sealed));
    }

    @Test
    void aPlaintextValueIsNotTakenForASealedOne() {
        assertFalse(CredentialCipher.isSealed("MIIEvQIBADANBgkqhkiG9w0BAQEFAASC"));
        assertFalse(CredentialCipher.isSealed(null));
        assertThrows(CredentialCipherException.class, () -> new CredentialCipher(KEY).unseal("MIIEvQ"));
    }

    @Test
    void aKeyDerivedFromASharedSecretIsTheSameOnBothSidesAndDiffersByPurpose() {
        String sealed = CredentialCipher.derivedFrom("purpose", "token").seal("value");

        assertEquals("value", CredentialCipher.derivedFrom("purpose", "token").unseal(sealed));
        assertThrows(CredentialCipherException.class, () -> CredentialCipher.derivedFrom("other purpose", "token")
                .unseal(sealed));
        assertThrows(CredentialCipherException.class, () -> CredentialCipher.derivedFrom("purpose", "other token")
                .unseal(sealed));
    }
}
