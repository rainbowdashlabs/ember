/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The one place SHA-256 is asked for.
 *
 * <p>Every caller wants the same two lines and has to catch the same exception for an algorithm the Java
 * platform is required to provide, so the ceremony lives here once and nowhere else.
 */
public final class Sha256 {

    private static final int BUFFER_BYTES = 8 * 1024;

    private Sha256() {}

    /**
     * The hash of these bytes, in lower case hex.
     *
     * @param data what to hash
     * @return sixty-four hex characters
     */
    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(bytes(data));
    }

    /**
     * The hash of this text, read as UTF-8.
     *
     * @param text what to hash
     * @return sixty-four hex characters
     */
    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * The hash of everything the stream still holds, read to its end. The stream is left open.
     *
     * @param stream what to hash
     * @return sixty-four hex characters
     * @throws IOException when reading the stream fails
     */
    public static String hex(InputStream stream) throws IOException {
        var digest = digest();
        byte[] buffer = new byte[BUFFER_BYTES];
        int read;
        while ((read = stream.read(buffer)) != -1) {
            digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * The start of the hash of this text, for a short fingerprint that has no need of all 256 bits.
     *
     * @param text  what to hash, read as UTF-8
     * @param chars how many hex characters to keep, at most sixty-four
     * @return the first {@code chars} hex characters of the hash
     */
    public static String hexPrefix(String text, int chars) {
        return hex(text).substring(0, chars);
    }

    /**
     * The raw hash of these bytes.
     *
     * @param data what to hash
     * @return thirty-two bytes
     */
    public static byte[] bytes(byte[] data) {
        return digest().digest(data);
    }

    /**
     * The raw hash of this text, read as UTF-8.
     *
     * @param text what to hash
     * @return thirty-two bytes
     */
    public static byte[] bytes(String text) {
        return bytes(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * A fresh digest, for a caller that feeds its input in several pieces.
     *
     * @return an unused SHA-256 digest
     */
    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform and is not here", e);
        }
    }
}
