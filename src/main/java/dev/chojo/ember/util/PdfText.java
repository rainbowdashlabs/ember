/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the plain text out of a PDF so it can be indexed for search. Extraction is best-effort:
 * a document that cannot be parsed simply yields no text rather than failing the operation that
 * produced it.
 */
public final class PdfText {
    private static final Logger log = LoggerFactory.getLogger(PdfText.class);

    private PdfText() {}

    /**
     * Extracts the text of a PDF document.
     *
     * @param data the PDF bytes
     * @return the extracted text, or {@code null} when the document could not be read
     */
    public static String extract(byte[] data) {
        try (var document = Loader.loadPDF(data)) {
            return new PDFTextStripper().getText(document);
        } catch (Exception e) {
            log.warn("Failed to extract text from PDF: {}", e.getMessage());
            return null;
        }
    }
}
