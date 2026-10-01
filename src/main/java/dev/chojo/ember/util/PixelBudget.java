/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.Refusal;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * The most pixels a picture may unpack into, checked from its header before a pixel is decoded: a PNG of a
 * few kilobytes can declare 30000 by 30000 pixels and ask for gigabytes. 50 megapixels still takes a full
 * resolution phone photograph (48 megapixels).
 */
public final class PixelBudget {
    /** The most pixels one picture may have, width times height. */
    public static final long MAX_PIXELS = 50_000_000L;

    private static final float POINTS_PER_INCH = 72f;

    private PixelBudget() {}

    /**
     * The size a picture declares in its header.
     *
     * @return the size, or empty when no installed reader recognises the bytes
     * @throws IOException when a reader recognises the bytes but cannot read the header
     */
    static Optional<Dimensions> measure(byte[] data) throws IOException {
        try (var stream = streamOf(data)) {
            var reader = readerFor(stream);
            if (reader.isEmpty()) return Optional.empty();
            try {
                return Optional.of(dimensionsOf(reader.get()));
            } finally {
                reader.get().dispose();
            }
        }
    }

    /** False only for a readable picture over the budget; bytes nothing can read cannot unpack into anything. */
    public static boolean fits(byte[] data) {
        try {
            return measure(data).map(Dimensions::fits).orElse(true);
        } catch (IOException e) {
            return true;
        }
    }

    /** Refuses a picture over the budget. */
    public static void requireWithin(byte[] data) {
        if (!fits(data)) throw Refusal.PICTURE_TOO_MANY_PIXELS.raise();
    }

    /**
     * Decodes a picture after measuring it with the reader that decodes it.
     *
     * @return the picture, or {@code null} when no installed reader recognises the bytes, as {@link ImageIO#read}
     * @throws IOException when the bytes are recognised but cannot be read
     */
    public static @Nullable BufferedImage read(byte[] data) throws IOException {
        try (var stream = streamOf(data)) {
            var reader = readerFor(stream);
            if (reader.isEmpty()) return null;
            try {
                if (!dimensionsOf(reader.get()).fits()) throw Refusal.PICTURE_TOO_MANY_PIXELS.raise();
                return reader.get().read(0, reader.get().getDefaultReadParam());
            } finally {
                reader.get().dispose();
            }
        }
    }

    /** The scale a PDF page is drawn at: the one the resolution asks for, lowered to stay inside the budget. */
    public static float pageScale(PDRectangle cropBox, int dpi) {
        float wanted = dpi / POINTS_PER_INCH;
        double area = (double) Math.max(cropBox.getWidth(), 1f) * Math.max(cropBox.getHeight(), 1f);
        float largest = (float) Math.sqrt(MAX_PIXELS / area);
        return Math.min(wanted, largest);
    }

    /** The pixel size of a PDF page drawn at a scale, rounded the way the renderer rounds. */
    public static Dimensions pageSize(PDRectangle cropBox, float scale) {
        int width = (int) Math.max(Math.floor(cropBox.getWidth() * scale), 1);
        int height = (int) Math.max(Math.floor(cropBox.getHeight() * scale), 1);
        return new Dimensions(width, height);
    }

    private static ImageInputStream streamOf(byte[] data) {
        return new MemoryCacheImageInputStream(new ByteArrayInputStream(data));
    }

    private static Optional<ImageReader> readerFor(ImageInputStream stream) {
        var readers = ImageIO.getImageReaders(stream);
        if (!readers.hasNext()) return Optional.empty();
        var reader = readers.next();
        reader.setInput(stream, true, true);
        return Optional.of(reader);
    }

    private static Dimensions dimensionsOf(ImageReader reader) throws IOException {
        return new Dimensions(reader.getWidth(0), reader.getHeight(0));
    }

    /** The size of a picture in pixels. */
    public record Dimensions(int width, int height) {
        /** Whether the picture has at most {@link #MAX_PIXELS} pixels. */
        public boolean fits() {
            return (long) width * height <= MAX_PIXELS;
        }
    }
}
