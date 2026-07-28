/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PdfTextTest {

    private static byte[] pdfContaining(String text) throws Exception {
        try (var document = new PDDocument()) {
            var page = new PDPage();
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            var out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void theWordsOfADocumentAreExtracted() throws Exception {
        assertTrue(PdfText.extract(pdfContaining("Loeschwasserversorgung")).contains("Loeschwasserversorgung"));
    }

    /**
     * A document that cannot be parsed yields no text rather than failing whatever produced it.
     */
    @Test
    void anUnreadableDocumentYieldsNothing() {
        assertNull(PdfText.extract("%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.UTF_8)));
        assertNull(PdfText.extract(new byte[] {0x00, 0x01, 0x02}));
    }
}
