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

class LegalNumberingTest {

    @Test
    void headingsAreNumberedAsParagraphs() {
        var result = LegalNumbering.apply("""
                ## Grundlagen

                Text.

                ## Nutzungsregeln

                ### Verbotene Nutzung
                """, LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("## § 1 Grundlagen"));
        assertTrue(result.markdown().contains("## § 2 Nutzungsregeln"));
        assertTrue(result.markdown().contains("### 2.1 Verbotene Nutzung"));
    }

    @Test
    void decimalStyleCountsWithoutParagraphSigns() {
        var result = LegalNumbering.apply("## Rechte\n\n### Auskunft\n", LegalNumbering.Style.DECIMAL);

        assertTrue(result.markdown().contains("## 1. Rechte"));
        assertTrue(result.markdown().contains("### 1.1 Auskunft"));
    }

    @Test
    void aReferenceCarriesTheNumberTheSectionEndsUpWith() {
        var result = LegalNumbering.apply("""
                ## Grundlagen

                Es gilt {{ ref:nutzungsregeln }}.

                ## Nutzungsregeln
                """, LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("Es gilt [§ 2](#nutzungsregeln)."));
        assertTrue(result.unresolved().isEmpty());
    }

    @Test
    void aReferenceMovesWithItsSection() {
        String withOrder = """
                ## Nutzungsregeln

                ## Grundlagen

                Siehe {{ ref:nutzungsregeln }}.
                """;

        var result = LegalNumbering.apply(withOrder, LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("[§ 1](#nutzungsregeln)"), "the number follows the new order");
    }

    @Test
    void aReferenceWithTitleNamesTheSection() {
        var result =
                LegalNumbering.apply("## Grundlagen\n\nSiehe {{ ref!grundlagen }}.\n", LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("[§ 1 (Grundlagen)](#grundlagen)"));
    }

    @Test
    void anExplicitAnchorSurvivesARename() {
        var result = LegalNumbering.apply("""
                ## Ganz anders benannte Regeln {#conduct}

                Siehe {{ ref:conduct }}.
                """, LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("## § 1 Ganz anders benannte Regeln"));
        assertFalse(result.markdown().contains("{#conduct}"), "the marker is not part of the text");
        assertTrue(result.markdown().contains("[§ 1](#conduct)"));
    }

    @Test
    void aMissingTargetIsVisibleRatherThanSilent() {
        var result = LegalNumbering.apply("## Grundlagen\n\nSiehe {{ ref:weg }}.\n", LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("**[Verweis fehlt: weg]**"));
        assertEquals(1, result.unresolved().size());
        assertTrue(result.unresolved().contains("weg"));
    }

    @Test
    void aNumberLeftInAHeadingIsReplacedRatherThanDoubled() {
        var result = LegalNumbering.apply("## § 22 Verfügbarkeit\n", LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("## § 1 Verfügbarkeit"));
        assertFalse(result.markdown().contains("§ 22"));
    }

    @Test
    void twoHeadingsOfTheSameNameKeepSeparateAnchors() {
        var result = LegalNumbering.apply("## Kontakt\n\n## Kontakt\n", LegalNumbering.Style.PARAGRAPH);

        assertEquals(2, result.anchors().size());
        assertEquals("kontakt", result.anchors().getFirst());
        assertEquals("kontakt-2", result.anchors().get(1));
    }

    @Test
    void headingsInCodeBlocksAreLeftAlone() {
        var result = LegalNumbering.apply("""
                ## Beispiel

                ```
                ## kein Abschnitt
                ```
                """, LegalNumbering.Style.PARAGRAPH);

        assertTrue(result.markdown().contains("## kein Abschnitt"), "a fenced block is content, not structure");
        assertEquals(1, result.anchors().size());
    }

    @Test
    void umlautsBecomeReadableAnchors() {
        assertEquals("verfuegbarkeit-und-wartung", LegalNumbering.slug("Verfügbarkeit und Wartung"));
        assertEquals("massnahmen", LegalNumbering.slug("Maßnahmen"));
    }

    @Test
    void theReferencedAnchorsOfADocumentAreListed() {
        var referenced = LegalNumbering.referencedAnchors("Siehe {{ ref:a }} und {{ ref!b }}.");

        assertEquals(2, referenced.size());
        assertTrue(referenced.contains("a"));
        assertTrue(referenced.contains("b"));
    }
}
