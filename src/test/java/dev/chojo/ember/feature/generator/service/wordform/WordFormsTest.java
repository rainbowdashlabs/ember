/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.entity.TemplateQuestion;
import dev.chojo.ember.feature.generator.entity.TemplateQuestionKind;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * A Word form turned into a PDF template's page with LibreOffice: the member's data where a label is
 * recognised, a question everywhere else, each field where the form had its place.
 */
class WordFormsTest {
    private static WordForms.Converted converted;

    @BeforeAll
    static void convert() throws IOException {
        byte[] docx;
        try (var in = Objects.requireNonNull(
                WordFormsTest.class.getClassLoader().getResourceAsStream("generator/change-form.docx"))) {
            docx = in.readAllBytes();
        }
        try {
            converted = WordForms.convert(
                    new WordForms.Form(docx, Map.of(), List.of(), Map.of(), Map.of("personalnummer", "profile.9")));
        } catch (IOException e) {
            assumeTrue(false, "LibreOffice is not available: " + e.getMessage());
        }
    }

    @Test
    void recognisedLabelsPrintTheMembersDataAndEveryOtherPlaceIsAsked() {
        assertEquals(
                List.of("Verein", "Bearbeiter", "Datum", "Name", "Vorname", "Geb.-Datum", "Eintritt", "Ausweisnummer"),
                converted.recognised());
        assertEquals(List.of(), converted.unplaced());
        assertEquals(
                List.of(
                        new TemplateQuestion("ist_am", "ist am", TemplateQuestionKind.DATE, "", false),
                        new TemplateQuestion(
                                "aus_dem_verein_ausgetreten",
                                "aus dem Verein ausgetreten",
                                TemplateQuestionKind.CHECK,
                                "",
                                false),
                        new TemplateQuestion(
                                "in_die_gruppe_gewechselt",
                                "in die Gruppe … gewechselt",
                                TemplateQuestionKind.CHECK,
                                "",
                                false),
                        new TemplateQuestion("in_die_gruppe", "in die Gruppe", TemplateQuestionKind.TEXT, "", false),
                        new TemplateQuestion(
                                "ist_umgezogen_neue_anschrift",
                                "ist umgezogen. Neue Anschrift",
                                TemplateQuestionKind.CHECK,
                                "Ja",
                                false),
                        new TemplateQuestion("strasse", "Straße", TemplateQuestionKind.TEXT, "", false),
                        new TemplateQuestion("plz_ort", "PLZ Ort", TemplateQuestionKind.TEXT, "", false),
                        new TemplateQuestion(
                                "beitrag_erstattet", "Beitrag erstattet", TemplateQuestionKind.CHECK, "Ja", false),
                        new TemplateQuestion("sonstiges", "Sonstiges", TemplateQuestionKind.TEXT, "keine", false)),
                converted.questions());
    }

    @Test
    void eachPlaceGetsAFieldWhereTheFormHadIt() {
        assertEquals(17, converted.fields().size());
        assertEquals(
                List.of(
                        "{{station.name}}",
                        "{{generatedBy.fullName}}",
                        "{{today}}",
                        "{{member.lastName}}",
                        "{{member.firstName}}",
                        "{{member.birthDate}}",
                        "{{member.joinDate}}",
                        "{{profile.9}}",
                        "{{ask.ist_am}}"),
                converted.fields().stream().limit(9).map(PdfField::text).toList());
        assertEquals(
                4,
                converted.fields().stream()
                        .filter(field -> field.kind() == PdfFieldKind.CHECK)
                        .count());
        var club = converted.fields().getFirst().rect();
        assertTrue(club.width() > 200, "a field on a line of its own runs to the margin: " + club);
        var group = converted.fields().stream()
                .filter(field -> "{{ask.in_die_gruppe}}".equals(field.text()))
                .findFirst()
                .orElseThrow()
                .rect();
        assertTrue(group.right() < club.right() - 100, "a field stops before the words after it: " + group);
        var name = converted.fields().get(3).rect();
        var firstName = converted.fields().get(4).rect();
        assertTrue(name.right() < firstName.x(), "a field in a row stops before the next one");
    }

    @Test
    void thePdfKeepsNoFormFieldOfItsOwn() throws IOException {
        try (var document = Loader.loadPDF(converted.pdf())) {
            assertNull(document.getDocumentCatalog().getAcroForm(null));
            for (var page : document.getPages())
                assertTrue(page.getAnnotations().isEmpty());
        }
    }
}
