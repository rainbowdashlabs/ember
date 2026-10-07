/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Checks for the bytes a file format starts with, which say what a file is whatever it is called.
 */
public final class ByteSignature {

    private ByteSignature() {}

    /**
     * @param data      the bytes of a file
     * @param signature the bytes a format starts with
     * @return whether the file starts with them
     */
    public static boolean startsWith(byte[] data, byte[] signature) {
        return matchesAt(data, 0, signature);
    }

    /**
     * @param data      the bytes of a file
     * @param signature the text a format starts with, in ASCII
     * @return whether the file starts with it
     */
    public static boolean startsWith(byte[] data, String signature) {
        return startsWith(data, signature.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * @param data     the bytes of a file
     * @param offset   where in the file the bytes are looked for
     * @param expected the bytes looked for
     * @return whether the file holds them there
     */
    public static boolean matchesAt(byte[] data, int offset, byte[] expected) {
        int end = offset + expected.length;
        return data.length >= end && Arrays.equals(data, offset, end, expected, 0, expected.length);
    }
}
