/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Pictures that claim to be far larger than they are, for proving that a claim is read and refused
 * before anybody decodes it.
 */
public final class OversizedPictures {
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private OversizedPictures() {}

    /**
     * A PNG of a few dozen bytes whose header declares the given size and which carries no pixels at
     * all. Decoding it would allocate the whole declared size before noticing the data is missing.
     *
     * @param width  the width the header claims
     * @param height the height the header claims
     * @return the bytes
     */
    public static byte[] pngClaiming(int width, int height) {
        var header = ByteBuffer.allocate(13)
                .putInt(width)
                .putInt(height)
                .put((byte) 8)
                .put((byte) 6)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) 0)
                .array();
        var out = new ByteArrayOutputStream();
        out.writeBytes(PNG_SIGNATURE);
        out.writeBytes(chunk("IHDR", header));
        out.writeBytes(chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    private static byte[] chunk(String type, byte[] data) {
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        var crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        return ByteBuffer.allocate(12 + data.length)
                .putInt(data.length)
                .put(typeBytes)
                .put(data)
                .putInt((int) crc.getValue())
                .array();
    }
}
