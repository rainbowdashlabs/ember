/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.owner.Owner;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How the fonts of a station, its association and the instance add up into the families a template sees. */
class ReachableFontsTest {
    private static final Owner STATION = new Owner.Station(1);
    private static final Owner ASSOCIATION = new Owner.Association(2);
    private static final Owner INSTANCE = new Owner.Instance();

    private static DocumentFont font(int id, Owner owner, String family, FontStyle style, FontOutline outline) {
        return new DocumentFont(id, owner, family, style, "f.ttf", outline, family, 1, "x", Instant.EPOCH);
    }

    private static DocumentFont font(int id, Owner owner, String family, FontStyle style) {
        return font(id, owner, family, style, FontOutline.TRUETYPE);
    }

    @Test
    void everyOwnersFamiliesAddUp() {
        var fonts = ReachableFonts.of(List.of(
                font(1, INSTANCE, "Instanz", FontStyle.REGULAR),
                font(2, ASSOCIATION, "Verband", FontStyle.REGULAR),
                font(3, STATION, "Wache", FontStyle.REGULAR)));
        assertEquals(
                List.of("Instanz", "Verband", "Wache"),
                fonts.families().stream().map(FontFamily::name).toList());
        assertEquals(FontOrigin.ASSOCIATION, fonts.find("verband").orElseThrow().origin());
        assertTrue(fonts.find(null).isEmpty());
        assertTrue(fonts.find(" ").isEmpty());
        assertTrue(fonts.find("Fehlt").isEmpty());
    }

    /** The nearest owner takes a name, ignoring case, with all its files and none of the others'. */
    @Test
    void theNearestOwnerWinsAClashWhole() {
        var fonts = ReachableFonts.of(List.of(
                font(1, INSTANCE, "Hausschrift", FontStyle.REGULAR),
                font(2, INSTANCE, "Hausschrift", FontStyle.BOLD),
                font(3, ASSOCIATION, "HAUSSCHRIFT", FontStyle.REGULAR),
                font(4, STATION, "hausschrift", FontStyle.ITALIC),
                font(5, ASSOCIATION, "Verband", FontStyle.REGULAR),
                font(6, INSTANCE, "verband", FontStyle.BOLD)));

        var house = fonts.find("Hausschrift").orElseThrow();
        assertEquals(FontOrigin.STATION, house.origin());
        assertEquals(List.of(FontStyle.ITALIC), house.styles());
        assertEquals(4, house.file(FontStyle.BOLD).id());

        var association = fonts.find("VERBAND").orElseThrow();
        assertEquals(FontOrigin.ASSOCIATION, association.origin());
        assertEquals(5, association.file(FontStyle.BOLD).id());
        assertEquals(2, fonts.families().size());
    }

    @Test
    void aStyleTheFamilyLacksFallsBackToItsRegularOne() {
        var family = ReachableFonts.of(List.of(
                        font(1, STATION, "Wache", FontStyle.BOLD), font(2, STATION, "Wache", FontStyle.REGULAR)))
                .find("Wache")
                .orElseThrow();
        assertEquals(1, family.file(FontStyle.BOLD).id());
        assertEquals(2, family.file(FontStyle.BOLD_ITALIC).id());
        assertEquals(List.of(FontStyle.REGULAR, FontStyle.BOLD), family.styles());
        assertEquals(List.of("Wache"), family.internalFamilies());
    }

    /** Fields on an uploaded PDF take TrueType outlines only. */
    @Test
    void aFamilyOfPostScriptOutlinesDoesNotPrintOnAPdf() {
        var fonts = ReachableFonts.of(List.of(
                font(1, STATION, "Gemischt", FontStyle.REGULAR),
                font(2, STATION, "Gemischt", FontStyle.BOLD, FontOutline.CFF),
                font(3, STATION, "Glatt", FontStyle.REGULAR)));
        var mixed = fonts.find("Gemischt").orElseThrow();
        assertFalse(mixed.printsOnPdf());
        assertTrue(mixed.pdfFile(FontStyle.BOLD).isEmpty());
        assertEquals(1, mixed.pdfFile(FontStyle.REGULAR).orElseThrow().id());
        assertTrue(fonts.find("Glatt").orElseThrow().printsOnPdf());
        assertTrue(ReachableFonts.none().families().isEmpty());
    }
}
