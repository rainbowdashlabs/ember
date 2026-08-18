/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbering has to survive the whole way from the files on disk to the served HTML.
 */
class LegalDocumentNumberingTest {

    private final LegalDocumentService service = new LegalDocumentService();

    private Path write(Path dir, String... sections) throws Exception {
        Path locale = dir.resolve("de");
        Files.createDirectories(locale);
        for (int i = 0; i < sections.length; i += 2) {
            Files.writeString(locale.resolve(sections[i]), sections[i + 1]);
        }
        return dir;
    }

    @Test
    void theTermsNumberTheirSectionsWhileRendering(@TempDir Path tmp) throws Exception {
        Path dir = write(
                tmp.resolve("tos"),
                "010-grundlagen.md",
                "## Grundlagen\n\nText.\n",
                "020-konto.md",
                "## Konto\n\nText.\n");

        var document = service.getDocument(dir, "de", "tos");

        assertTrue(document.html().contains("§ 1 Grundlagen"));
        assertTrue(document.html().contains("§ 2 Konto"));
    }

    @Test
    void aSectionSwitchedOnLaterTakesTheNumberAfterIt(@TempDir Path tmp) throws Exception {
        Path dir = write(
                tmp.resolve("tos"),
                "010-grundlagen.md",
                "## Grundlagen\n",
                "015-mailversand.md",
                "## Mailversand\n",
                "020-konto.md",
                "## Konto\n");

        var document = service.getDocument(dir, "de", "tos");

        assertTrue(document.html().contains("§ 2 Mailversand"));
        assertTrue(document.html().contains("§ 3 Konto"), "everything behind it moves along");
    }

    @Test
    void aReferenceIsRenderedAsALinkToTheSection(@TempDir Path tmp) throws Exception {
        Path dir = write(
                tmp.resolve("tos"),
                "010-grundlagen.md",
                "## Grundlagen\n\nEs gilt {{ ref:konto }}.\n",
                "020-konto.md",
                "## Konto {#konto}\n");

        var document = service.getDocument(dir, "de", "tos");

        assertTrue(document.html().contains("§ 2"), "the reference carries the number of its target");
        assertFalse(document.html().contains("{{ ref"), "no placeholder survives into the page");
    }

    @Test
    void anUnnumberedDocumentStaysUnnumbered(@TempDir Path tmp) throws Exception {
        Path dir = write(tmp.resolve("privacy"), "010-allgemein.md", "## Verantwortlicher\n\nText.\n");

        var document = service.getDocument(dir, "de", "privacy");

        assertTrue(document.html().contains(">Verantwortlicher<"));
        assertFalse(document.html().contains("1. Verantwortlicher"));
    }

    @Test
    void theShippedTermsKeepTheNumbersTheyWereWrittenWith() {
        var document = service.getDocument(Path.of("templates/data/documents/tos"), "de", "tos");

        assertTrue(document.html().contains("§ 1 "), "the first paragraph stays the first");
        assertTrue(document.html().contains("§ 29 "), "and the last stays the last");
        assertFalse(document.html().contains("§ 30 "), "the switched-off section does not count");
    }
}
