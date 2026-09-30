/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import javax.imageio.ImageIO;

/**
 * What a stored file looks like on a tile: an image is its own picture, a PDF its first page, anything
 * else has none.
 */
public final class FilePicture {
    private static final String PDF = "application/pdf";

    private FilePicture() {}

    /** Whether a file of this stored type has a picture. */
    public static boolean exists(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/") || PDF.equals(mimeType));
    }

    /**
     * The picture of a file as image bytes.
     *
     * @param dpi how finely a PDF page is drawn
     * @return the file itself for an image, its first page as a PNG for a PDF, and empty otherwise
     */
    public static Optional<byte[]> of(String mimeType, byte[] data, int dpi) throws IOException {
        if (mimeType != null && mimeType.startsWith("image/")) return Optional.of(data);
        if (!PDF.equals(mimeType)) return Optional.empty();
        var page = firstPage(data, dpi);
        if (page == null) return Optional.empty();
        var out = new ByteArrayOutputStream();
        ImageIO.write(page, "png", out);
        return Optional.of(out.toByteArray());
    }

    /**
     * The first page of a PDF, drawn at a scale lowered to stay inside the {@link PixelBudget}, since a page
     * can declare any size.
     *
     * @return the page, or {@code null} without pages or when no scale fits the budget
     */
    public static BufferedImage firstPage(byte[] pdf, int dpi) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0) return null;
            var cropBox = document.getPage(0).getCropBox();
            float scale = PixelBudget.pageScale(cropBox, dpi);
            if (!PixelBudget.pageSize(cropBox, scale).fits()) return null;
            return new PDFRenderer(document).renderImage(0, scale);
        }
    }
}
