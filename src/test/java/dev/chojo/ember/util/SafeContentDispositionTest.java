/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeContentDispositionTest {

    @Test
    void simpleAsciiFilenameRoundTrips() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "notes.pdf");
        assertEquals("attachment; filename=\"notes.pdf\"; filename*=UTF-8''notes.pdf", header);
    }

    @Test
    void inlineDispositionEmitsInlineToken() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.INLINE, "report.pdf");
        assertTrue(header.startsWith("inline; "));
    }

    @Test
    void crLfQuoteBackslashAreStripped() {
        String header =
                SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "evil\r\nclean\"\\.txt");
        assertFalse(header.contains("\r"));
        assertFalse(header.contains("\n"));
        assertEquals(1, countDoubleQuotedRuns(header, "filename="));
        assertFalse(header.contains("\\"));
        assertTrue(header.contains("evilclean.txt"));
    }

    private static int countDoubleQuotedRuns(String header, String afterToken) {
        int from = header.indexOf(afterToken);
        int firstQuote = header.indexOf('"', from);
        int closeQuote = header.indexOf('"', firstQuote + 1);
        if (firstQuote < 0 || closeQuote < 0) return 0;
        return header.substring(firstQuote + 1, closeQuote).contains("\"") ? 2 : 1;
    }

    @Test
    void controlCharsAndDelAreStripped() {
        String name = "ab\u0001cd\u007Fef.txt";
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, name);
        assertFalse(header.contains("\u0001"));
        assertFalse(header.contains("\u007F"));
        assertTrue(header.contains("abcdef.txt"));
    }

    @Test
    void nonAsciiIsPercentEncodedInFilenameStar() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "Frühling.txt");
        assertTrue(header.contains("filename*=UTF-8''Fr%C3%BChling.txt"));
    }

    @Test
    void asciiFallbackReplacesNonAsciiWithUnderscore() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "Frühling.txt");
        assertTrue(header.contains("filename=\"Fr_hling.txt\""));
    }

    @Test
    void blankFilenameProducesGenericFallback() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "");
        assertTrue(header.contains("filename=\"download\""));
        assertTrue(header.contains("filename*=UTF-8''"));
    }

    @Test
    void nullFilenameProducesGenericFallback() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, null);
        assertTrue(header.contains("filename=\"download\""));
    }

    @Test
    void wholeStringOfControlCharsBecomesFallback() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "\r\n\"\\\u0001");
        assertTrue(header.contains("filename=\"download\""));
        assertFalse(header.contains("\r"));
        assertFalse(header.contains("\n"));
    }

    @Test
    void cyrillicNameIsPercentEncoded() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.ATTACHMENT, "файл.pdf");
        assertTrue(header.contains("filename*=UTF-8''%D1%84%D0%B0%D0%B9%D0%BB.pdf"));
        assertTrue(header.contains("filename=\"____.pdf\""));
    }

    @Test
    void emojiNameIsPercentEncoded() {
        String header = SafeContentDisposition.build(SafeContentDisposition.Disposition.INLINE, "🚀.png");
        assertTrue(header.contains("filename*=UTF-8''%F0%9F%9A%80.png"));
        assertTrue(header.startsWith("inline; "));
    }
}
