/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTokensTest {

    @Test
    void bytesHaveTheLengthAskedFor() {
        assertEquals(20, RandomTokens.bytes(20).length);
    }

    @Test
    void urlSafeTokensSurviveAPathSegment() {
        String token = RandomTokens.urlSafe(32);
        assertEquals(43, token.length(), "32 bytes in unpadded Base64");
        assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
        assertEquals(32, Base64.getUrlDecoder().decode(token).length);
    }

    @Test
    void base64KeysUseTheStandardPaddedAlphabet() {
        String key = RandomTokens.base64(32);
        assertEquals(44, key.length());
        assertTrue(key.endsWith("="), key);
        assertEquals(32, Base64.getDecoder().decode(key).length);
    }

    @Test
    void hexTokensAreLowerCaseAndTwiceTheBytes() {
        assertTrue(RandomTokens.hex(32).matches("[0-9a-f]{64}"));
    }

    @Test
    void readableCodesStayInsideBothOlderAlphabets() {
        for (int i = 0; i < 200; i++) {
            String code = RandomTokens.readableCode(8);
            assertTrue(code.matches("[23456789ABCDEFGHJKMNPQRSTVWXYZ]{8}"), code);
            assertTrue(code.matches("[23456789BCDFGHJKLMNPQRSTVWXZ]{8}"), code);
        }
    }

    @Test
    void codesDrawOnlyFromTheirAlphabet() {
        assertTrue(RandomTokens.code("ab", 50).matches("[ab]{50}"));
    }

    @Test
    void twoTokensAreNotTheSame() {
        assertNotEquals(RandomTokens.urlSafe(32), RandomTokens.urlSafe(32));
    }

    @Test
    void numbersStayBelowTheBound() {
        IntStream.range(0, 200).forEach(i -> {
            int n = RandomTokens.number(3);
            assertTrue(n >= 0 && n < 3, "drew " + n);
        });
    }

    @Test
    void shufflingKeepsEveryElement() {
        List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6));
        RandomTokens.shuffle(list);
        assertEquals(new HashSet<>(List.of(1, 2, 3, 4, 5, 6)), new HashSet<>(list));
    }
}
