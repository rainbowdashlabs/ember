/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalImportServiceTest {

    private static final String LAWYERS_DOCUMENT = """
            # Nutzungsbedingungen

            ## § 1 Geltungsbereich

            (1) Diese Bedingungen gelten für alle Nutzer.

            ## § 2 Pflichten der Nutzer

            (1) Es gelten die Regeln aus § 1 entsprechend.

            (2) Bei Verstößen gilt § 3.

            ## § 3 Sperrung

            (1) Der Betreiber kann den Zugang sperren.
            """;

    @Test
    void aNumberedDocumentBecomesSectionsWithoutNumbers() {
        var imported = LegalImportService.normalise(LAWYERS_DOCUMENT);

        assertEquals("Nutzungsbedingungen", imported.title());
        assertEquals(3, imported.sections().size());
        assertEquals("010-geltungsbereich.md", imported.sections().getFirst().fileName());
        assertTrue(imported.sections().getFirst().content().contains("## Geltungsbereich {#geltungsbereich}"));
        assertFalse(imported.sections().getFirst().content().contains("§ 1 Geltungsbereich"));
    }

    @Test
    void referencesInTheTextPointAtAnchorsAfterwards() {
        var imported = LegalImportService.normalise(LAWYERS_DOCUMENT);

        String duties = imported.sections().get(1).content();
        assertTrue(duties.contains("aus {{ ref:geltungsbereich }} entsprechend"));
        assertTrue(duties.contains("gilt {{ ref:sperrung }}"), "a reference may point forwards");
        assertEquals(2, imported.references());
    }

    @Test
    void theImportedDocumentRenumbersItself() {
        var imported = LegalImportService.normalise(LAWYERS_DOCUMENT);
        String assembled = String.join(
                "\n\n",
                imported.sections().stream()
                        .map(LegalImportService.Section::content)
                        .toList());

        var rendered = LegalNumbering.apply(assembled, LegalNumbering.Style.PARAGRAPH);

        assertTrue(rendered.markdown().contains("## § 1 Geltungsbereich"));
        assertTrue(rendered.markdown().contains("## § 3 Sperrung"));
        assertTrue(rendered.markdown().contains("[§ 1](#geltungsbereich)"));
        assertTrue(rendered.unresolved().isEmpty());
    }

    @Test
    void aNumberWithoutASectionIsLeftAloneAndReported() {
        var imported = LegalImportService.normalise("""
                ## § 1 Geltungsbereich

                Es gilt § 99 des Bürgerlichen Gesetzbuchs.
                """);

        assertTrue(imported.sections().getFirst().content().contains("§ 99 des Bürgerlichen"));
        assertEquals(1, imported.unmatched().size());
        assertTrue(imported.unmatched().contains("§ 99"));
    }

    @Test
    void otherSpellingsOfAReferenceAreUnderstood() {
        var imported = LegalImportService.normalise("""
                ## Ziffer 1 Grundlagen

                Siehe Abschnitt 1 sowie Ziffer 1.
                """);

        assertEquals(2, imported.references());
        assertTrue(imported.sections().getFirst().content().contains("Siehe {{ ref:grundlagen }} sowie"));
    }

    @Test
    void aDocumentWithoutNumbersKeepsItsHeadingsAndGainsAnchors() {
        var imported = LegalImportService.normalise("## Grundlagen\n\nText.\n");

        assertTrue(imported.sections().getFirst().content().contains("## Grundlagen {#grundlagen}"));
        assertEquals(0, imported.references());
    }

    @Test
    void textBeforeTheFirstSectionIsKept() {
        var imported = LegalImportService.normalise("""
                # Datenschutzerklärung

                Diese Erklärung gilt für die gesamte Plattform.

                ## Verantwortlicher

                Der Betreiber.
                """);

        assertEquals(2, imported.sections().size());
        assertEquals("010-einleitung.md", imported.sections().getFirst().fileName());
        assertTrue(imported.sections().getFirst().content().contains("gilt für die gesamte Plattform"));
    }

    @Test
    void nothingInMakesNothingOut() {
        var imported = LegalImportService.normalise("   ");

        assertTrue(imported.sections().isEmpty());
        assertEquals(0, imported.references());
    }
}
