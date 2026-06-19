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

class SafeInlineMimeTest {

    @Test
    void allowsPng() {
        assertEquals("image/png", SafeInlineMime.safeContentType("image/png"));
        assertTrue(SafeInlineMime.isInlineSafe("image/png"));
    }

    @Test
    void allowsJpegWebpGifPdf() {
        assertEquals("image/jpeg", SafeInlineMime.safeContentType("image/jpeg"));
        assertEquals("image/webp", SafeInlineMime.safeContentType("image/webp"));
        assertEquals("image/gif", SafeInlineMime.safeContentType("image/gif"));
        assertEquals("application/pdf", SafeInlineMime.safeContentType("application/pdf"));
    }

    @Test
    void forcesHtmlToOctetStream() {
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("text/html"));
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("application/xhtml+xml"));
        assertFalse(SafeInlineMime.isInlineSafe("text/html"));
    }

    @Test
    void forcesSvgToOctetStream() {
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("image/svg+xml"));
        assertFalse(SafeInlineMime.isInlineSafe("image/svg+xml"));
    }

    @Test
    void forcesJavascriptToOctetStream() {
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("application/javascript"));
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("text/javascript"));
    }

    @Test
    void nullAndBlankBecomeOctetStream() {
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType(null));
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType(""));
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("   "));
        assertFalse(SafeInlineMime.isInlineSafe(null));
    }

    @Test
    void stripsCharsetParameter() {
        assertEquals("image/png", SafeInlineMime.safeContentType("image/png; charset=binary"));
        assertTrue(SafeInlineMime.isInlineSafe("application/pdf; q=1"));
    }

    @Test
    void caseInsensitive() {
        assertEquals("image/png", SafeInlineMime.safeContentType("IMAGE/PNG"));
        assertTrue(SafeInlineMime.isInlineSafe("Image/Jpeg"));
    }

    @Test
    void unknownTypeBecomesOctetStream() {
        assertEquals("application/octet-stream", SafeInlineMime.safeContentType("application/x-fictional"));
    }
}
