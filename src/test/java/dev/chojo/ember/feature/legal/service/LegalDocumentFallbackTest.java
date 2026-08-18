/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A legal page is served to everyone, logged in or not. It must never come back blank, whatever
 * state the data directory is in.
 */
class LegalDocumentFallbackTest {

    private final LegalDocumentService service = new LegalDocumentService();

    @Test
    void anEmptyDirectoryFallsBackToTheBundledDocument(@TempDir Path dir) {
        var document = service.getDocument(dir.resolve("privacy"), "de", "privacy");

        assertFalse(document.html().isBlank(), "an empty directory must not produce an empty page");
        assertTrue(document.markdown().contains("Datenschutzerklärung"));
    }

    @Test
    void theFallbackCarriesTheGeneratedStorageDisclosure(@TempDir Path dir) {
        var document = service.getDocument(dir.resolve("privacy"), "de", "privacy");

        assertTrue(
                document.markdown().contains("Speicherung im Browser"),
                "the generated section belongs in the fallback as much as in a document on disk");
        assertTrue(document.markdown().contains("session_token"));
    }

    @Test
    void aLocaleWithoutABundledDocumentFallsBackToTheDefaultOne(@TempDir Path dir) {
        var document = service.getDocument(dir.resolve("tos"), "xx", "tos");

        assertFalse(document.html().isBlank());
        assertTrue(document.markdown().contains("Nutzungsbedingungen"));
    }

    @Test
    void aDocumentOnDiskWinsOverTheBundledOne(@TempDir Path dir) throws Exception {
        Path locale = dir.resolve("privacy").resolve("de");
        java.nio.file.Files.createDirectories(locale);
        java.nio.file.Files.writeString(locale.resolve("01-own.md"), "# Unsere eigene Erklärung");

        var document = service.getDocument(dir.resolve("privacy"), "de", "privacy");

        assertEquals("# Unsere eigene Erklärung", document.markdown().strip());
    }

    @Test
    void anUnknownTypeStaysEmptyRatherThanGuessing(@TempDir Path dir) {
        var document = service.getDocument(dir.resolve("nonsense"), "de", "nonsense");

        assertTrue(document.markdown().isEmpty());
    }
}
