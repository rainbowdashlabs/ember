/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfwriter.compress.CompressParameters;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * Opens and saves the PDFs templates fill in.
 *
 * <p>A PDF protected by an owner password only (no printing, no changes) opens without one, and its
 * protection is taken off, so what is filled in is saved as a plain PDF. A PDF that needs a password
 * to be opened at all, or is protected by a scheme that cannot be taken off, is refused.
 *
 * <p>Saving writes no object streams. A PDF/A-1 does not allow them, and a PDF that was PDF/A before
 * it was filled in should still be one afterwards.
 */
public final class PdfFiles {

    private PdfFiles() {}

    /**
     * Opens a PDF with any protection taken off.
     *
     * @param data the bytes
     * @return the document, which the caller closes
     */
    public static PDDocument open(byte[] data) {
        PDDocument document;
        try {
            document = Loader.loadPDF(data);
        } catch (InvalidPasswordException e) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_PASSWORD.raise();
        } catch (IOException e) {
            throw protectionIn(e)
                    ? DocumentRefusal.DOCUMENT_TEMPLATE_PDF_PROTECTED.raise()
                    : DocumentRefusal.DOCUMENT_TEMPLATE_PDF_UNREADABLE.raise();
        }
        if (document.isEncrypted()) document.setAllSecurityToBeRemoved(true);
        return document;
    }

    /**
     * Saves a document as a new file.
     *
     * @param document the document
     * @return the bytes
     * @throws IOException where it cannot be written, as for protection that could not be taken off
     */
    public static byte[] save(PDDocument document) throws IOException {
        var out = new ByteArrayOutputStream();
        document.save(out, CompressParameters.NO_COMPRESSION);
        return out.toByteArray();
    }

    /** Whether a PDF failed to open because of how it is protected rather than how it is written. */
    private static boolean protectionIn(IOException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) continue;
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("security handler") || lower.contains("decrypt") || lower.contains("encrypt")) {
                return true;
            }
        }
        return false;
    }
}
