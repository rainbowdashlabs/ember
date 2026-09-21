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
 * What a stored file looks like, for a tile that shows it rather than naming it.
 *
 * <p>An image is its own picture and a PDF is its first page. Anything else has none, and the tile
 * says what kind of file it is instead. The one place that answers it, so member documents, the
 * media library and the wiki cannot come to three different opinions about the same file.
 */
public final class FilePicture {
    private static final String PDF = "application/pdf";

    private FilePicture() {}

    /**
     * Whether a file of this type has a picture to be made of it.
     *
     * @param mimeType the stored type of the file
     */
    public static boolean exists(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/") || PDF.equals(mimeType));
    }

    /**
     * The picture of a file, as image bytes ready to be stored.
     *
     * @param mimeType the stored type of the file
     * @param data     the file's bytes
     * @param dpi      how finely a PDF page is drawn, which is what decides the size of its picture
     * @return the file itself for an image, its first page as a PNG for a PDF, and empty for anything
     *     else or a PDF with no pages
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
     * The first page of a PDF, drawn.
     *
     * @return the page, or {@code null} for a document with no pages
     */
    public static BufferedImage firstPage(byte[] pdf, int dpi) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            if (document.getNumberOfPages() == 0) return null;
            return new PDFRenderer(document).renderImageWithDPI(0, dpi);
        }
    }
}
