/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import dev.chojo.ember.feature.generator.entity.FieldRect;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfFieldKind;
import dev.chojo.ember.feature.generator.service.wordform.FormSpot.FormSpotKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A field laid over a place prints like the text the form sets there: in its size, and in the family
 * chosen for the family of the place.
 */
class FormLayoutTest {
    private static final FormWidgets.Taken TAKEN = new FormWidgets.Taken(
            new byte[0],
            Map.of(
                    "ember-f1", new FieldRect(1, 100, 700, 80, 14),
                    "ember-f2", new FieldRect(1, 100, 650, 80, 14),
                    "ember-f3", new FieldRect(1, 100, 600, 12, 12)),
            new double[] {595},
            List.of());

    private static final List<FormSpot> SPOTS = List.of(
            new FormSpot(1, FormSpotKind.TEXT, "Name", null),
            new FormSpot(2, FormSpotKind.TEXT, "Sonstiges", null),
            new FormSpot(3, FormSpotKind.CHECK, "Beitrag erstattet", null));

    @Test
    void aTextFieldPrintsInTheSizeAndTheChosenFamilyOfItsPlace() {
        var fields = FormLayout.lay(
                        SPOTS,
                        TAKEN,
                        0,
                        Map.of(
                                "ember-f1", new TextFont("Frutiger", 9.5),
                                "ember-f2", new TextFont("Wingdings", 12),
                                "ember-f3", new TextFont("Frutiger", 9.5)),
                        Map.of("Frutiger", "Open Sans"),
                        Map.of())
                .fields();

        assertField(fields.get(0), 9.5, "Open Sans");
        assertField(fields.get(1), 12, null);
        assertField(fields.get(2), FormLayout.FONT_SIZE, null);
    }

    @Test
    void aPlaceWithoutAFontReadPrintsInTheDefaultSize() {
        var fields =
                FormLayout.lay(SPOTS, TAKEN, 0, Map.of(), Map.of(), Map.of()).fields();

        assertField(fields.get(0), FormLayout.FONT_SIZE, null);
    }

    @Test
    void theFieldsAndTheFormItselfAreFilledWithTheSameExampleValues() {
        var laid = FormLayout.lay(SPOTS, TAKEN, 0, Map.of(), Map.of(), Map.of());

        var byTag = FormLayout.samples(SPOTS, Map.of());

        assertEquals(
                Map.of("ember-f1", "Max Mustermann", "ember-f2", "Beispieltext", "ember-f3", FormSamples.YES), byTag);
        assertEquals(
                Map.of(
                        "member.fullName", "Max Mustermann",
                        "ask.sonstiges", "Beispieltext",
                        "ask.beitrag_erstattet", FormSamples.YES),
                laid.samples());
    }

    private static void assertField(PdfField field, double size, String family) {
        assertEquals(size, field.fontSize());
        assertEquals(field.kind() == PdfFieldKind.TEXT, field.keepSize(), "a text field keeps its size");
        if (family == null) assertNull(field.fontFamily());
        else assertEquals(family, field.fontFamily());
    }
}
