/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.media.image;

import net.coobird.thumbnailator.Thumbnails;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.OptionalInt;

/**
 * The names a picture set is stored under, and which side of the picture its sizes measure.
 *
 * <p>Two layouts exist on disk and both are read as they are, which is what keeps every stored file
 * found under the name it has. The sized families write {@code original.<ext>} and {@code <size>.<ext>}
 * measured on the longest side; the media library writes {@code orig.<ext>} and {@code w<width>.webp}
 * measured on the width, and files the drawn first page of a document apart from them as
 * {@code page1.webp} and {@code page1-w<width>.webp}. Only the base of a name is read, never its
 * extension, so a size written as PNG by an older build and one written as WebP now are the same size.
 */
public enum VariantLayout {
    /** {@code original.<ext>} and {@code <size>.<ext>}, measured on the longest side. */
    SIZED("original", "", Side.LONGEST),
    /** {@code orig.<ext>} and {@code w<width>.<ext>}, measured on the width. */
    LIBRARY("orig", "w", Side.WIDTH),
    /** {@code page1.<ext>} and {@code page1-w<width>.<ext>}: the drawn first page of a library document. */
    LIBRARY_PAGE("page1", "page1-w", Side.WIDTH);

    private final String originalBase;
    private final String sizePrefix;
    private final Side side;

    VariantLayout(String originalBase, String sizePrefix, Side side) {
        this.originalBase = originalBase;
        this.sizePrefix = sizePrefix;
        this.side = side;
    }

    /** The base name the full picture is stored under. */
    public String originalBase() {
        return originalBase;
    }

    /**
     * The file name of the full picture.
     *
     * @param extension the extension without its dot
     * @return the file name
     */
    public String originalName(String extension) {
        return originalBase + "." + extension;
    }

    /**
     * The file name of one size.
     *
     * @param size   the size, in pixels on the side this layout measures
     * @param format the format it is written in
     * @return the file name
     */
    public String sizeName(int size, ImageFormat format) {
        return sizePrefix + size + "." + format.extension();
    }

    /**
     * Whether a stored file is the full picture of a set in this layout.
     *
     * @param file the stored file
     * @return whether its base is this layout's original base
     */
    public boolean isOriginal(VariantFile file) {
        return originalBase.equals(file.base());
    }

    /**
     * The size a stored file holds, read from its base alone.
     *
     * @param file the stored file
     * @return the size, or empty when the name is not a size of this layout
     */
    public OptionalInt sizeOf(VariantFile file) {
        String base = file.base();
        if (!base.startsWith(sizePrefix)) return OptionalInt.empty();
        String digits = base.substring(sizePrefix.length());
        if (digits.isEmpty() || digits.length() > 5 || !digits.chars().allMatch(Character::isDigit)) {
            return OptionalInt.empty();
        }
        int size = Integer.parseInt(digits);
        return size > 0 ? OptionalInt.of(size) : OptionalInt.empty();
    }

    /**
     * How large a picture is on the side this layout measures.
     *
     * @param image the picture
     * @return its longest side or its width, in pixels
     */
    public int measure(BufferedImage image) {
        return side.of(image);
    }

    /**
     * The picture scaled so that the side this layout measures is {@code size} pixels.
     *
     * @param image the picture
     * @param size  the size wanted
     * @return the scaled picture
     * @throws IOException when the picture cannot be scaled
     */
    public BufferedImage scale(BufferedImage image, int size) throws IOException {
        double factor = (double) size / side.of(image);
        int width = Math.max(1, (int) Math.round(image.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(image.getHeight() * factor));
        return Thumbnails.of(image).forceSize(width, height).asBufferedImage();
    }

    /** The side of a picture a size is measured on. */
    private enum Side {
        LONGEST {
            @Override
            int of(BufferedImage image) {
                return Math.max(image.getWidth(), image.getHeight());
            }
        },
        WIDTH {
            @Override
            int of(BufferedImage image) {
                return image.getWidth();
            }
        };

        abstract int of(BufferedImage image);
    }
}
