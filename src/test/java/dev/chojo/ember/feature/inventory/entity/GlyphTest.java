/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.entity;

import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The picture a row of gear is drawn with.
 *
 * <p>What is worth holding on to is the reading of absence: a blank string and a missing field are
 * the same answer, and a colour is compared in one spelling however it was typed.
 */
class GlyphTest {
    @Test
    void blanksReadAsNothingChosen() {
        assertEquals(Glyph.NONE, Glyph.of(null, null));
        assertEquals(Glyph.NONE, Glyph.of("", "   "));
        assertEquals(Glyph.NONE, Glyph.of("  ", ""));
    }

    @Test
    void halvesAreIndependent() {
        assertEquals(new Glyph("helmet-safety", null), Glyph.of("helmet-safety", null));
        assertEquals(new Glyph(null, "#2563eb"), Glyph.of(null, "#2563eb"));
    }

    @Test
    void aColourIsKeptInOneSpelling() {
        assertEquals("#2563eb", Glyph.of("shirt", "  #2563EB ").color());
        assertEquals("shirt", Glyph.of("  shirt  ", null).icon());
    }

    @Test
    void anAbsentColourIsPaintable() {
        assertTrue(Glyph.NONE.validColor());
        assertDoesNotThrow(Glyph.NONE::requirePaintable);
        assertNull(Glyph.NONE.color());
    }

    @Test
    void onlyRrggbbIsPaintable() {
        assertDoesNotThrow(Glyph.of(null, "#abcdef")::requirePaintable);
        assertThrows(BadRequestResponse.class, Glyph.of(null, "#abc")::requirePaintable);
        assertThrows(BadRequestResponse.class, Glyph.of(null, "2563eb")::requirePaintable);
        assertThrows(BadRequestResponse.class, Glyph.of(null, "rebeccapurple")::requirePaintable);
        assertThrows(BadRequestResponse.class, Glyph.of(null, "#2563ebff")::requirePaintable);
    }

    @Test
    void anIconIsNotCheckedAgainstAnything() {
        assertDoesNotThrow(Glyph.of("a-name-no-catalogue-offers", null)::requirePaintable);
    }

    /** What a caller built by hand is normalised too, so a column never holds two spellings of one colour. */
    @Test
    void paintableNormalisesWhatItChecks() {
        assertEquals(new Glyph("shirt", "#2563eb"), new Glyph("shirt", "#2563EB").paintable());
        assertEquals(Glyph.NONE, new Glyph("", "  ").paintable());
        assertThrows(BadRequestResponse.class, new Glyph("shirt", "blau")::paintable);
    }
}
