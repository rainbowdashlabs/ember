/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 without the checked exception for an algorithm every Java platform must provide. Text is read
 * as UTF-8 and hex is lower case.
 */
public final class Sha256 {

    private Sha256() {}

    /** The hash of these bytes as sixty-four hex characters. */
    public static String hex(byte[] data) {
        return HexFormat.of().formatHex(digest().digest(data));
    }

    /** The hash of this text as sixty-four hex characters. */
    public static String hex(String text) {
        return hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** The hash of what the stream still holds as sixty-four hex characters; the stream is left open. */
    public static String hex(InputStream stream) throws IOException {
        var digest = digest();
        stream.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
        return HexFormat.of().formatHex(digest.digest());
    }

    /** The first {@code chars} hex characters of the hash of this text, for a short fingerprint. */
    public static String hexPrefix(String text, int chars) {
        return hex(text).substring(0, chars);
    }

    /** The raw thirty-two byte hash of this text. */
    public static byte[] bytes(String text) {
        return digest().digest(text.getBytes(StandardCharsets.UTF_8));
    }

    /** A fresh digest, for input fed in several pieces. */
    public static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform and is not here", e);
        }
    }
}
