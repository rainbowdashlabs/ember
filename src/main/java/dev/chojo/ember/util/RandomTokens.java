/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

/**
 * Secrets drawn from the system's cryptographic source: tokens, keys, salts and codes a person types.
 *
 * <p>The readable alphabet leaves out vowels and look-alikes ({@code 0/O}, {@code 1/I/L}, {@code U/V}), so a
 * code read aloud never spells a word; it is the common part of the two alphabets used before, so every code
 * it draws was valid under both.
 */
public final class RandomTokens {

    private static final String READABLE_ALPHABET = "23456789BCDFGHJKMNPQRSTVWXZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomTokens() {}

    /** {@code count} random bytes. */
    public static byte[] bytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /** {@code byteCount} random bytes in URL-safe Base64 without padding, for paths, queries and cookies. */
    public static String urlSafe(int byteCount) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(byteCount));
    }

    /** {@code byteCount} random bytes in padded standard Base64, the shape configuration keys take. */
    public static String base64(int byteCount) {
        return Base64.getEncoder().encodeToString(bytes(byteCount));
    }

    /** {@code byteCount} random bytes in lower case hex. */
    public static String hex(int byteCount) {
        return HexFormat.of().formatHex(bytes(byteCount));
    }

    /** A code of {@code length} characters from the readable alphabet, without grouping. */
    public static String readableCode(int length) {
        return code(READABLE_ALPHABET, length);
    }

    /** A code of {@code length} characters, each drawn evenly from {@code alphabet}. */
    public static String code(String alphabet, int length) {
        var code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /** A number from zero up to {@code bound}, exclusive. */
    public static int number(int bound) {
        return RANDOM.nextInt(bound);
    }

    /** Reorders the list in place, unpredictably. */
    public static void shuffle(List<?> list) {
        Collections.shuffle(list, RANDOM);
    }
}
