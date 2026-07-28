/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.feature.knowledgebase.service.KbFileTypeDetector.detect;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KbFileTypeDetectorTest {

    @Test
    void theReportedMimeTypeDecidesTheType() {
        assertEquals(
                KbFileType.PRESENTATION,
                detect("application/vnd.openxmlformats-officedocument.presentationml.presentation", "slides.pptx"));
        assertEquals(KbFileType.PRESENTATION, detect("application/vnd.ms-powerpoint", "old.ppt"));
        assertEquals(KbFileType.PRESENTATION, detect("application/vnd.oasis.opendocument.presentation", "deck.odp"));
        assertEquals(KbFileType.PDF, detect("application/pdf", "document.pdf"));
        assertEquals(KbFileType.IMAGE, detect("image/png", "photo.png"));
        assertEquals(KbFileType.MARKDOWN, detect("text/markdown", "doc.md"));
        assertEquals(KbFileType.TEXT, detect("text/plain", "notes.txt"));
    }

    /**
     * An upload arriving without a MIME type is still recognised from its extension, so a known
     * document is not stored as an opaque blob.
     */
    @Test
    void theExtensionDecidesWhenNoMimeTypeArrives() {
        assertEquals(KbFileType.PRESENTATION, detect(null, "talk.pptx"));
        assertEquals(KbFileType.PRESENTATION, detect(null, "legacy.PPT"));
        assertEquals(KbFileType.PRESENTATION, detect(null, "talk.odp"));
        assertEquals(KbFileType.PDF, detect(null, "report.pdf"));
        assertEquals(KbFileType.MARKDOWN, detect(null, "readme.markdown"));
        assertEquals(KbFileType.MARKDOWN, detect(null, "readme.md"));
        assertEquals(KbFileType.TEXT, detect(null, "notes.txt"));
        for (String extension : new String[] {"png", "jpg", "jpeg", "gif", "webp", "svg"}) {
            assertEquals(KbFileType.IMAGE, detect(null, "img." + extension));
        }
    }

    /**
     * A markdown file uploaded as plain bytes is still treated as markdown, so it renders instead
     * of being served as raw text.
     */
    @Test
    void aMarkdownExtensionWinsOverAGenericTextMimeType() {
        assertEquals(KbFileType.MARKDOWN, detect("text/plain", "guide.md"));
    }

    @Test
    void anythingUnrecognisedIsStoredAsAnOpaqueFile() {
        assertEquals(KbFileType.OTHER, detect("application/x-custom", "data.bin"));
        assertEquals(KbFileType.OTHER, detect(null, "data.bin"));
        assertEquals(KbFileType.OTHER, detect(null, null));
        assertEquals(KbFileType.OTHER, detect("application/octet-stream", null));
    }
}
