/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The one detection every document import shares: the bytes decide where they can, the name and the
 * declared type where they cannot, and the old binary Word format is never taken.
 */
class PandocFormatTest {
    private static final byte[] OLD_WORD = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};

    private static byte[] fixture(String path) throws IOException {
        try (var in = PandocFormatTest.class.getResourceAsStream(path)) {
            return Objects.requireNonNull(in, path).readAllBytes();
        }
    }

    @Test
    void wordOpenDocumentAndEpubAreKnownByTheirContent() throws IOException {
        assertEquals(Optional.of("docx"), PandocConverter.formatOf(fixture("/generator/certificate.docx"), null, null));
        assertEquals(Optional.of("odt"), PandocConverter.formatOf(fixture("/generator/certificate.odt"), null, null));
        assertEquals(Optional.of("epub"), PandocConverter.formatOf(fixture("/pandoc/sample.epub"), null, null));
    }

    /** A file is what its bytes say, whatever it was named or declared as. */
    @Test
    void aRenamedFileIsKnownByItsContent() throws IOException {
        assertEquals(
                Optional.of("odt"),
                PandocConverter.formatOf(fixture("/generator/certificate.odt"), "brief.docx", "application/msword"));
        assertEquals(
                Optional.of("docx"),
                PandocConverter.formatOf(fixture("/generator/certificate.docx"), "brief.epub", "text/html"));
    }

    @Test
    void theOldWordFormatIsNeverTaken() {
        assertEquals(Optional.empty(), PandocConverter.formatOf(OLD_WORD, "alt.doc", "application/msword"));
        assertEquals(Optional.empty(), PandocConverter.formatOf(OLD_WORD, "alt.docx", null));
        assertEquals(
                Optional.empty(),
                PandocConverter.formatOf("text".getBytes(StandardCharsets.UTF_8), "alt.doc", "application/msword"));
        assertTrue(PandocConverter.isContainer(OLD_WORD));
    }

    @Test
    void anArchiveOfAnyOtherKindIsNoDocument() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("notes.txt"));
            zip.write("hallo".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertEquals(Optional.empty(), PandocConverter.formatOf(out.toByteArray(), "notes.docx", null));
        assertTrue(PandocConverter.isContainer(out.toByteArray()));
        assertEquals(Optional.empty(), PandocConverter.formatOf(new byte[] {0x50, 0x4B, 0x03, 0x04, 1}, "x.odt", null));
    }

    @Test
    void textFormatsAreKnownByNameOrDeclaredType() {
        byte[] text = "<p>Hallo</p>".getBytes(StandardCharsets.UTF_8);

        assertEquals(Optional.of("html"), PandocConverter.formatOf(text, "seite.HTM", null));
        assertEquals(Optional.of("html"), PandocConverter.formatOf(text, null, "text/html; charset=utf-8"));
        assertEquals(
                Optional.of("rtf"),
                PandocConverter.formatOf("{\\rtf1 Hallo}".getBytes(StandardCharsets.UTF_8), null, null));
        assertEquals(Optional.of("rtf"), PandocConverter.formatOf(text, null, "application/rtf"));
        assertEquals(Optional.of("latex"), PandocConverter.formatOf(text, "artikel.tex", null));
        assertEquals(Optional.empty(), PandocConverter.formatOf(text, "notizen.md", "text/markdown"));
        assertFalse(PandocConverter.isContainer(text));
    }
}
