/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import dev.chojo.ember.util.WebpEncoder;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageEncoderTest {
    private final ImageEncoder encoder = new ImageEncoder();
    private final BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);

    @Test
    void writesPngAndJpegInTheirOwnFormat() throws IOException {
        assertTrue(encoder.writes(ImageFormat.PNG));
        assertTrue(encoder.writes(ImageFormat.JPEG));
        assertEquals(Optional.of(ImageFormat.PNG), ImageFormat.sniff(encoder.encode(image, ImageFormat.PNG)));
        assertEquals(Optional.of(ImageFormat.JPEG), ImageFormat.sniff(encoder.encode(image, ImageFormat.JPEG)));
    }

    @Test
    void writesWebpExactlyWhereCwebpIsInstalled() throws IOException {
        assertEquals(WebpEncoder.isAvailable(), encoder.writes(ImageFormat.WEBP));
        if (WebpEncoder.isAvailable()) {
            assertEquals(Optional.of(ImageFormat.WEBP), ImageFormat.sniff(encoder.encode(image, ImageFormat.WEBP)));
        }
    }

    @Test
    void neverWritesAGif() {
        assertFalse(encoder.writes(ImageFormat.GIF));
        assertThrows(IOException.class, () -> encoder.encode(image, ImageFormat.GIF));
    }
}
