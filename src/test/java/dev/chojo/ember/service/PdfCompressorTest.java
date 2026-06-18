/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Storage;
import dev.chojo.ember.feature.storage.service.PdfCompressor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class PdfCompressorTest {

    private Storage config;
    private PdfCompressor compressor;

    @BeforeEach
    void setup() {
        config = Mockito.mock(Storage.class);
        Mockito.when(config.compressPdfs()).thenReturn(true);
        Mockito.when(config.compressThresholdBytes()).thenReturn(0L);
        compressor = new PdfCompressor(config);
        PdfCompressor.resetAvailabilityCacheForTests();
    }

    @Test
    void shouldCompressMatchesGate() {
        Assumptions.assumeTrue(PdfCompressor.isAvailable(), "qpdf not available — skipping");
        assertTrue(compressor.shouldCompress("application/pdf", 1024));
        assertFalse(compressor.shouldCompress("application/pdf", 0));
        assertFalse(compressor.shouldCompress("text/plain", 1024));
        assertFalse(compressor.shouldCompress(null, 1024));
    }

    @Test
    void shouldCompressFalseWhenToggleOff() {
        Mockito.when(config.compressPdfs()).thenReturn(false);
        assertFalse(compressor.shouldCompress("application/pdf", 1024));
    }

    @Test
    void compressReturnsOriginalWhenQpdfUnavailable() throws IOException {
        Assumptions.assumeFalse(PdfCompressor.isAvailable(), "qpdf present — skipping unavailable-path");
        byte[] pdf = simplePdf();
        byte[] result = compressor.compress(pdf);
        assertArrayEquals(pdf, result);
    }

    @Test
    void compressRoundTripsWhenAvailable() throws IOException {
        Assumptions.assumeTrue(PdfCompressor.isAvailable(), "qpdf not available — skipping");
        byte[] pdf = simplePdf();
        byte[] result = compressor.compress(pdf);
        assertNotNull(result);
        assertTrue(result.length > 0);
        try (var doc = Loader.loadPDF(result)) {
            assertEquals(1, doc.getNumberOfPages());
        }
    }

    @Test
    void compressReturnsOriginalOnBrokenInput() {
        Assumptions.assumeTrue(PdfCompressor.isAvailable(), "qpdf not available — skipping");
        byte[] junk = "not a pdf".getBytes();
        byte[] result = compressor.compress(junk);
        assertArrayEquals(junk, result);
    }

    private static byte[] simplePdf() throws IOException {
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var contents = new PDPageContentStream(doc, page)) {
                contents.beginText();
                contents.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contents.newLineAtOffset(72, 720);
                contents.showText("Hello PDF " + "world ".repeat(200));
                contents.endText();
            }
            var out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }
}
