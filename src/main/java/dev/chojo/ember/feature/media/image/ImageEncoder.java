/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import dev.chojo.ember.util.WebpEncoder;
import jakarta.inject.Singleton;
import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Writes a decoded picture in one of the stored formats.
 *
 * <p>PNG and JPEG go through Thumbnailator, JPEG at quality 0.85. WebP goes through the {@code cwebp}
 * binary at quality 88, high enough that the small text of a drawn page stays sharp, because no Java
 * image writer for it is on the classpath; where the host has no
 * {@code cwebp}, WebP is not written at all and {@link #writes} says so. A GIF is never written: a GIF
 * is kept as it came, since writing one again keeps only its first frame.
 */
@Singleton
public class ImageEncoder {
    private static final double JPEG_QUALITY = 0.85;
    private static final int WEBP_QUALITY = 88;

    /**
     * Whether pictures can be written in a format on this host.
     *
     * @param format the format
     * @return true for PNG and JPEG, for WebP where {@code cwebp} is installed, never for GIF
     */
    public boolean writes(ImageFormat format) {
        return switch (format) {
            case PNG, JPEG -> true;
            case WEBP -> WebpEncoder.isAvailable();
            case GIF -> false;
        };
    }

    /**
     * Writes a picture in a format, at the size it has.
     *
     * @param image  the picture
     * @param format a format {@link #writes} answers true for
     * @return the encoded bytes
     * @throws IOException when the picture cannot be written in that format
     */
    public byte[] encode(BufferedImage image, ImageFormat format) throws IOException {
        return switch (format) {
            case PNG, JPEG -> thumbnailator(image, format);
            case WEBP -> webp(image);
            case GIF -> throw new IOException("A GIF is kept as it came and never written");
        };
    }

    private static byte[] thumbnailator(BufferedImage image, ImageFormat format) throws IOException {
        var out = new ByteArrayOutputStream();
        Thumbnails.of(image)
                .scale(1)
                .outputQuality(JPEG_QUALITY)
                .outputFormat(format.extension())
                .toOutputStream(out);
        return out.toByteArray();
    }

    private static byte[] webp(BufferedImage image) throws IOException {
        try {
            return WebpEncoder.encode(image, WEBP_QUALITY);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while writing WebP", e);
        }
    }
}
