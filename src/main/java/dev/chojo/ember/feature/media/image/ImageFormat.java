/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * The raster formats a picture is taken in, recognised by the bytes it starts with.
 *
 * <p>The type a browser declares for an upload is advice and nothing more: it can name anything. The
 * first bytes of the file cannot be changed without changing the format itself, so they are what
 * decides, and anything that is none of these four is not taken as a picture at all. That is what
 * keeps a script dressed up as an image out of storage.
 */
public enum ImageFormat {
    PNG("image/png", "png"),
    JPEG("image/jpeg", "jpg"),
    GIF("image/gif", "gif"),
    WEBP("image/webp", "webp");

    private static final int[] PNG_SIGNATURE = {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final int[] JPEG_SIGNATURE = {0xFF, 0xD8, 0xFF};
    private static final int[] RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final int[] WEBP_TAG = {0x57, 0x45, 0x42, 0x50};
    private static final int[] GIF_PREFIX = {0x47, 0x49, 0x46, 0x38};
    private static final int WEBP_TAG_OFFSET = 8;

    private final String mimeType;
    private final String extension;

    ImageFormat(String mimeType, String extension) {
        this.mimeType = mimeType;
        this.extension = extension;
    }

    /**
     * The format the bytes are in, read from their signature.
     *
     * @param data the bytes of the file
     * @return the format, or empty when the bytes are none of the four
     */
    public static Optional<ImageFormat> sniff(byte[] data) {
        if (data == null || data.length < 4) return Optional.empty();
        if (startsWith(data, PNG_SIGNATURE, 0)) return Optional.of(PNG);
        if (startsWith(data, JPEG_SIGNATURE, 0)) return Optional.of(JPEG);
        if (startsWith(data, RIFF, 0) && startsWith(data, WEBP_TAG, WEBP_TAG_OFFSET)) return Optional.of(WEBP);
        if (startsWith(data, GIF_PREFIX, 0)
                && data.length >= 6
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return Optional.of(GIF);
        }
        return Optional.empty();
    }

    /**
     * The format a file extension stands for, ignoring case.
     *
     * @param extension the extension without its dot, {@code jpeg} included
     * @return the format, or empty for an extension of anything else
     */
    public static Optional<ImageFormat> ofExtension(String extension) {
        if (extension == null) return Optional.empty();
        String lower = extension.toLowerCase(Locale.ROOT);
        if (lower.equals("jpeg")) return Optional.of(JPEG);
        return Arrays.stream(values()).filter(f -> f.extension.equals(lower)).findFirst();
    }

    /**
     * The format a MIME type names, ignoring case and parameters.
     *
     * @param mimeType the type, such as {@code image/png}
     * @return the format, or empty for a type of anything else
     */
    public static Optional<ImageFormat> ofMimeType(String mimeType) {
        if (mimeType == null) return Optional.empty();
        String bare = mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(f -> f.mimeType.equals(bare)).findFirst();
    }

    /** The MIME type the format is served as. */
    public String mimeType() {
        return mimeType;
    }

    /** The extension a file in this format is stored under, without its dot. */
    public String extension() {
        return extension;
    }

    private static boolean startsWith(byte[] data, int[] signature, int offset) {
        if (data.length < offset + signature.length) return false;
        for (int i = 0; i < signature.length; i++) {
            if ((data[offset + i] & 0xff) != signature[i]) return false;
        }
        return true;
    }
}
