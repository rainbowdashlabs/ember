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
 * The one mint for secrets that are drawn at random: session and link tokens, keys, salts and the
 * short codes a person types from a screen.
 *
 * <p>Everything here comes from the system's cryptographic source. The encodings are the ones the
 * stored and shown values already use, so a value minted here looks exactly like one minted before.
 *
 * <p>The readable alphabet is the one every typed code shares: digits and consonants without the
 * characters a reader confuses ({@code 0/O}, {@code 1/I/L}, {@code U/V}) and without vowels, so a
 * code read out loud never spells a word. It is the common part of the two alphabets that were in
 * use before, so every code drawn from it would have been a valid code under either of them.
 */
public final class RandomTokens {

    /** Digits and consonants a reader cannot mistake for one another. */
    public static final String READABLE_ALPHABET = "23456789BCDFGHJKMNPQRSTVWXZ";

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomTokens() {}

    /**
     * Fresh random bytes.
     *
     * @param count how many
     * @return {@code count} bytes from the cryptographic source
     */
    public static byte[] bytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * A token that survives being a path segment, a query parameter or a cookie.
     *
     * @param byteCount how many random bytes it carries
     * @return the bytes in URL-safe Base64 without padding
     */
    public static String urlSafe(int byteCount) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes(byteCount));
    }

    /**
     * A key in the standard Base64 alphabet with padding, the shape configuration files expect.
     *
     * @param byteCount how many random bytes it carries
     * @return the bytes in standard Base64
     */
    public static String base64(int byteCount) {
        return Base64.getEncoder().encodeToString(bytes(byteCount));
    }

    /**
     * A token in lower case hex.
     *
     * @param byteCount how many random bytes it carries
     * @return twice {@code byteCount} hex characters
     */
    public static String hex(int byteCount) {
        return HexFormat.of().formatHex(bytes(byteCount));
    }

    /**
     * A code a person types from a screen, drawn from {@link #READABLE_ALPHABET}.
     *
     * @param length how many characters
     * @return the code, without any grouping
     */
    public static String readableCode(int length) {
        return code(READABLE_ALPHABET, length);
    }

    /**
     * A code drawn from the given characters, each equally likely.
     *
     * @param alphabet the characters to draw from
     * @param length   how many characters
     * @return the code
     */
    public static String code(String alphabet, int length) {
        var code = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            code.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /**
     * A number from the cryptographic source.
     *
     * @param bound the upper bound, exclusive
     * @return a number from zero up to {@code bound}
     */
    public static int number(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * Puts a list into an order nobody can predict.
     *
     * @param list the list to reorder in place
     */
    public static void shuffle(List<?> list) {
        Collections.shuffle(list, RANDOM);
    }
}
