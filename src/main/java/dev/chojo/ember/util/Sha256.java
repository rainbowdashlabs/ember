/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

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

    private Sha256() {}

    /**
     * The hash of these bytes, in lower case hex.
     *
     * @param data what to hash
     * @return sixty-four hex characters
     */
    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(digest().digest(data));
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

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform and is not here", e);
        }
    }
}
