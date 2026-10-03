/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import jakarta.inject.Singleton;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Objects;

/**
 * The fonts text is stamped onto a PDF in.
 *
 * <p>Liberation Sans ships with the application ({@code fonts/LiberationSans-Regular.ttf}, under the SIL
 * Open Font License beside it), so the text reads the same on every installation, whatever fonts the
 * system has. It is embedded as a subset, which is what keeps a filled PDF/A a PDF/A, and it is the
 * last font every chain falls back to.
 */
@Singleton
public class StampFonts {
    private static final String LIBERATION_SANS = "fonts/LiberationSans-Regular.ttf";

    private final byte[] liberationSans = read(LIBERATION_SANS);

    /**
     * The fonts for text on one document, loaded into it.
     *
     * @param document the document the text is drawn into
     * @return the chain, Liberation Sans last
     * @throws IOException where the font cannot be embedded
     */
    public FontChain chainFor(PDDocument document) throws IOException {
        return new FontChain(List.of(liberationSans(document)));
    }

    /**
     * Liberation Sans, loaded into a document as a subset.
     *
     * @param document the document
     * @return the font
     * @throws IOException where it cannot be embedded
     */
    public PDType0Font liberationSans(PDDocument document) throws IOException {
        return PDType0Font.load(document, new ByteArrayInputStream(liberationSans), true);
    }

    private static byte[] read(String resource) {
        try (var in = Objects.requireNonNull(
                StampFonts.class.getClassLoader().getResourceAsStream(resource), "The application ships " + resource)) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The font " + resource + " could not be read", e);
        }
    }
}
