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

/**
 * What a reader finds in their downloads folder.
 *
 * <p>Two things are being held here. A name reads the same way everywhere, so the same export is
 * recognisable next to a different one. And a name built partly from what somebody typed is still a
 * name afterwards, whatever they typed.
 */
class DocumentNameTest {

    @Test
    void partsAreJoinedInReadingOrder() {
        assertEquals("Anwesenheit - Januar 2026.pdf", DocumentName.of("pdf", "Anwesenheit", "Januar 2026"));
    }

    @Test
    void aBareYearKeepsTheSeparator() {
        assertEquals("Anwesenheit - 2026.pdf", DocumentName.of("pdf", "Anwesenheit", "2026"));
    }

    /** A caller may pass a part it does not always have without asking whether it has one. */
    @Test
    void partsThatAreNotThereAreLeftOut() {
        assertEquals("Anwesenheit - Januar 2026.pdf", DocumentName.of("pdf", "Anwesenheit", null, "  ", "Januar 2026"));
    }

    @Test
    void aNameAlwaysSaysSomething() {
        assertEquals("Export.pdf", DocumentName.of("pdf", (String) null));
        assertEquals("Export.csv", DocumentName.of("csv"));
    }

    @Test
    void aBorrowedTitleKeepsItsOwnWords() {
        assertEquals("Übungsdienst am Gerät", DocumentName.part("Übungsdienst am Gerät"));
    }

    /** A slash would read as a folder and a colon is refused outright on some systems. */
    @Test
    void whatAFilesystemRefusesIsRemoved() {
        String part = DocumentName.part("Dienst: 3/4 <Gruppe> \"A\" | B\\C ?");
        assertFalse(part.contains("/"));
        assertFalse(part.contains(":"));
        assertFalse(part.contains("\\"));
        assertFalse(part.contains("\""));
        assertEquals("Dienst 3 4 Gruppe A B C", part);
    }

    /** A title carrying the separator would otherwise read as two parts of the name. */
    @Test
    void aTitleCannotInventAnExtraPart() {
        assertEquals("Dienst Gruppe A", DocumentName.part("Dienst - Gruppe A"));
    }

    @Test
    void aTitleCannotSmuggleInALineBreak() {
        assertEquals("Dienst Gruppe", DocumentName.part("Dienst\r\nGruppe"));
    }

    @Test
    void anOverlongTitleIsCutOnAWordBoundary() {
        String long_ = "Gemeinsame Abschlussübung der Jugendgruppen aus dem gesamten Landkreis am Wochenende";
        String part = DocumentName.part(long_);

        assertTrue(part.length() <= 60, part);
        assertFalse(part.endsWith(" "));
        assertTrue(long_.startsWith(part), part);
        assertTrue(part.contains(" "), part);
    }

    @Test
    void aSingleEndlessWordIsCutAnyway() {
        String part = DocumentName.part("A".repeat(200));

        assertEquals(60, part.length());
    }

    @Test
    void aTitleOfNothingUsableDisappearsRatherThanBecomingNoise() {
        assertEquals("", DocumentName.part("///"));
        assertEquals("Anwesenheit.pdf", DocumentName.of("pdf", "Anwesenheit", DocumentName.part("///")));
    }
}
