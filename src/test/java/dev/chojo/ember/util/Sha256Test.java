/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Sha256Test {

    private static final String ABC = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";

    @Test
    void textIsHashedAsUtf8() {
        assertEquals(ABC, Sha256.hex("abc"));
        assertEquals(ABC, Sha256.hex("abc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void aStreamHashesToWhatItsBytesHashTo() throws IOException {
        byte[] large = new byte[20_000];
        for (int i = 0; i < large.length; i++) large[i] = (byte) i;
        assertEquals(Sha256.hex(large), Sha256.hex(new ByteArrayInputStream(large)));
    }

    @Test
    void thePrefixIsTheStartOfTheHex() {
        assertEquals(ABC.substring(0, 16), Sha256.hexPrefix("abc", 16));
    }

    @Test
    void rawBytesAreTheHexDecoded() {
        assertArrayEquals(HexFormat.of().parseHex(ABC), Sha256.bytes("abc"));
    }

    @Test
    void aDigestFedInPiecesMatchesTheWhole() {
        var digest = Sha256.digest();
        digest.update("a".getBytes(StandardCharsets.UTF_8));
        digest.update("bc".getBytes(StandardCharsets.UTF_8));
        assertEquals(ABC, HexFormat.of().formatHex(digest.digest()));
    }
}
