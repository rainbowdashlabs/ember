/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.api.Refusal;
import org.apache.pdfbox.pdmodel.common.PDRectangle;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/**
 * The most pixels a picture may unpack into, and the one place a picture is decoded with that limit
 * held.
 *
 * <p>A compressed picture states its size in a few bytes of its header, and decoding it allocates
 * that much memory whatever the file itself weighs: a PNG of a few kilobytes can declare 30000 by
 * 30000 pixels and ask for gigabytes. Every picture is therefore measured from its header before a
 * single pixel is decoded, and one larger than {@link #MAX_PIXELS} is refused. A PDF page is drawn at
 * a scale that keeps it inside the same budget.
 *
 * <p>The budget is 50 megapixels, which still takes the full-resolution photographs of current
 * phone cameras (48 megapixels) while keeping a single decode to a few hundred megabytes.
 */
public final class PixelBudget {
    /** The most pixels one picture may have, width times height. */
    public static final long MAX_PIXELS = 50_000_000L;

    private static final float POINTS_PER_INCH = 72f;

    private PixelBudget() {}

    /**
     * The size a picture declares in its header, read without decoding a pixel.
     *
     * @param data the picture's bytes
     * @return the size, or empty when no installed reader recognises the bytes
     * @throws IOException when a reader recognises the bytes but cannot read the header
     */
    public static Optional<Dimensions> measure(byte[] data) throws IOException {
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

    /**
     * Whether a picture fits the budget. Bytes no reader recognises, or whose header cannot be read,
     * fit: nothing here can decode them either, so they cannot unpack into anything.
     *
     * @param data the picture's bytes
     * @return false only for a readable picture larger than {@link #MAX_PIXELS}
     */
    public static boolean fits(byte[] data) {
        try {
            return measure(data).map(Dimensions::fits).orElse(true);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Refuses a picture larger than the budget, before anything has been stored of it.
     *
     * @param data the picture's bytes
     */
    public static void requireWithin(byte[] data) {
        if (!fits(data)) throw Refusal.PICTURE_TOO_MANY_PIXELS.raise();
    }

    /**
     * Decodes a picture, having measured it first with the same reader that decodes it.
     *
     * @param data the picture's bytes
     * @return the picture, or {@code null} when no installed reader recognises the bytes, the same
     *     answer {@link ImageIO#read} gives
     * @throws IOException when the bytes are recognised but cannot be read
     */
    public static BufferedImage read(byte[] data) throws IOException {
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

    /**
     * The scale a PDF page is drawn at: the one the resolution asks for, lowered where the page is
     * so large that it would come out above the budget.
     *
     * @param cropBox the visible area of the page, in points
     * @param dpi     the resolution the caller would like
     * @return the scale, where 1 is 72 dots per inch
     */
    public static float pageScale(PDRectangle cropBox, int dpi) {
        float wanted = dpi / POINTS_PER_INCH;
        double area = (double) Math.max(cropBox.getWidth(), 1f) * Math.max(cropBox.getHeight(), 1f);
        float largest = (float) Math.sqrt(MAX_PIXELS / area);
        return Math.min(wanted, largest);
    }

    /**
     * The size a PDF page comes out at when drawn at a scale, counted the way the renderer counts
     * it.
     *
     * @param cropBox the visible area of the page, in points
     * @param scale   the scale it is drawn at
     * @return the size in pixels
     */
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

    /**
     * The size of a picture in pixels.
     *
     * @param width  pixels across
     * @param height pixels down
     */
    public record Dimensions(int width, int height) {
        /**
         * How many pixels the picture has.
         *
         * @return width times height
         */
        public long pixels() {
            return (long) width * height;
        }

        /**
         * Whether the picture fits the budget.
         *
         * @return true when it has at most {@link #MAX_PIXELS} pixels
         */
        public boolean fits() {
            return pixels() <= MAX_PIXELS;
        }
    }
}
