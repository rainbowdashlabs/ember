/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.media.image.ImageFormat;
import dev.chojo.ember.feature.signing.entity.SignaturePicture;
import dev.chojo.ember.util.PixelBudget;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * Turns a picture of a signature into the clean picture that is drawn into documents: the ink in one dark
 * colour on a transparent background, cut to the signature with a small margin and no larger than a
 * signature field needs.
 *
 * <p>The same steps serve a signature drawn on the screen and a photo or scan of one on paper. Each pixel
 * is laid over white, which makes a transparent background white, and compared with the brightness of the
 * background, taken as the brightness most of the picture is at least as dark as. What is clearly darker
 * than the background is ink, with soft edges where it is only a little darker; the paper, its grain and
 * light shadows fall away. Whatever colour the ink had, it comes out in one dark blue, as from a pen.
 *
 * <p>The picture is decoded only after its header was measured against the pixel budget, and anything
 * larger than a signature field could show is scaled down first, so a large photo costs no more than a
 * small one from there on.
 */
public final class SignatureImages {
    /** The largest file taken, a phone photo with room to spare. */
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    /** The widest a cleaned signature is kept. */
    static final int MAX_WIDTH = 1200;

    /** The tallest a cleaned signature is kept. */
    static final int MAX_HEIGHT = 400;

    private static final int WORKING_SIDE = 1600;
    private static final int INK_RGB = 0x1B2A4A;
    private static final int INK_FROM = 40;
    private static final int INK_RANGE = 80;
    private static final int VISIBLE_INK = 48;
    private static final int MARGIN = 8;
    private static final int MIN_SIDE = 4;
    private static final double BACKGROUND_SHARE = 0.9;

    private SignatureImages() {}

    /**
     * Cleans a picture of a signature.
     *
     * @param data the picture as sent: PNG, JPEG or WebP
     * @return the cleaned picture
     * @throws dev.chojo.ember.api.refusal.RefusalResponse with {@link DocumentRefusal#SIGNATURE_IMAGE_TOO_LARGE}
     *     for a file over {@link #MAX_BYTES}, {@link DocumentRefusal#SIGNATURE_IMAGE_NOT_A_PICTURE} for one
     *     that is no such picture, and {@link DocumentRefusal#SIGNATURE_IMAGE_EMPTY} where nothing on it
     *     stands out from the background
     */
    public static SignaturePicture clean(byte[] data) {
        if (data.length > MAX_BYTES) throw DocumentRefusal.SIGNATURE_IMAGE_TOO_LARGE.raise();
        if (ImageFormat.sniff(data).isEmpty()) throw DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE.raise();
        BufferedImage decoded;
        try {
            decoded = PixelBudget.read(data);
        } catch (IOException e) {
            throw DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE.raise();
        }
        if (decoded == null) throw DocumentRefusal.SIGNATURE_IMAGE_NOT_A_PICTURE.raise();
        var image = fitted(decoded, WORKING_SIDE, WORKING_SIDE);
        int[] ink = inkOf(image);
        var box = Box.around(ink, image.getWidth(), image.getHeight());
        if (box.width() < MIN_SIDE && box.height() < MIN_SIDE) throw DocumentRefusal.SIGNATURE_IMAGE_EMPTY.raise();
        var cut = cut(ink, image.getWidth(), box.withMargin(MARGIN, image.getWidth(), image.getHeight()));
        var picture = fitted(cut, MAX_WIDTH, MAX_HEIGHT);
        return new SignaturePicture(png(picture), picture.getWidth(), picture.getHeight());
    }

    /** How strongly each pixel is ink, from 0 for background to 255 for full ink, row by row. */
    private static int[] inkOf(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] brightness = new int[width * height];
        int[] counts = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = overWhite(image.getRGB(x, y));
                brightness[y * width + x] = value;
                counts[value]++;
            }
        }
        int background = background(counts, brightness.length);
        int[] ink = new int[brightness.length];
        for (int i = 0; i < brightness.length; i++) {
            int darker = background - brightness[i] - INK_FROM;
            ink[i] = Math.clamp(darker * 255L / INK_RANGE, 0, 255);
        }
        return ink;
    }

    /** The brightness of a pixel laid over white, from 0 for black to 255 for white or fully transparent. */
    private static int overWhite(int argb) {
        int alpha = argb >>> 24;
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = argb & 0xFF;
        int luma = (299 * red + 587 * green + 114 * blue) / 1000;
        return 255 - alpha * (255 - luma) / 255;
    }

    /** The brightness {@link #BACKGROUND_SHARE} of the pixels are at least as dark as. */
    private static int background(int[] counts, int pixels) {
        long wanted = (long) Math.ceil(pixels * BACKGROUND_SHARE);
        long seen = 0;
        for (int value = 0; value < counts.length; value++) {
            seen += counts[value];
            if (seen >= wanted) return value;
        }
        return 255;
    }

    private static BufferedImage cut(int[] ink, int width, Box box) {
        var out = new BufferedImage(box.width(), box.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < box.height(); y++) {
            for (int x = 0; x < box.width(); x++) {
                int alpha = ink[(box.top() + y) * width + box.left() + x];
                out.setRGB(x, y, (alpha << 24) | INK_RGB);
            }
        }
        return out;
    }

    /** The picture scaled down to fit the bounds, keeping its proportions; a picture within them as it is. */
    private static BufferedImage fitted(BufferedImage image, int maxWidth, int maxHeight) {
        double scale =
                Math.min(1.0, Math.min((double) maxWidth / image.getWidth(), (double) maxHeight / image.getHeight()));
        if (scale >= 1.0 && image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        int width = Math.max(1, (int) Math.round(image.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(image.getHeight() * scale));
        var out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(image, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    private static byte[] png(BufferedImage image) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("A signature picture could not be written", e);
        }
        return out.toByteArray();
    }

    /** The part of a picture that holds ink, in pixels. */
    private record Box(int left, int top, int width, int height) {

        static Box around(int[] ink, int width, int height) {
            int left = width;
            int top = height;
            int right = -1;
            int bottom = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (ink[y * width + x] < VISIBLE_INK) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            if (right < 0) return new Box(0, 0, 0, 0);
            return new Box(left, top, right - left + 1, bottom - top + 1);
        }

        Box withMargin(int margin, int width, int height) {
            int newLeft = Math.max(0, left - margin);
            int newTop = Math.max(0, top - margin);
            int newRight = Math.min(width, left + this.width + margin);
            int newBottom = Math.min(height, top + this.height + margin);
            return new Box(newLeft, newTop, newRight - newLeft, newBottom - newTop);
        }
    }
}
