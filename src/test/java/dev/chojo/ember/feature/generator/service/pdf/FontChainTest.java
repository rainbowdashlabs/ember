/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A text cut into runs: each character in the first font that has it, Liberation Sans behind the
 * chosen one, and what no font has named.
 */
class FontChainTest {

    /** Helvetica as a standard font knows no "Ł"; that one letter falls back, the rest stays. */
    @Test
    void aGlyphTheChosenFontLacksFallsBackForThatRunOnly() throws IOException {
        try (var document = new PDDocument()) {
            var chosen = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            var fallback = new StampFonts(DefaultFont.absent()).liberationSans(document);
            var chain = new FontChain(List.of(chosen, fallback));

            var shaped = chain.shape("Łukasz Weiß");

            assertEquals(2, shaped.runs().size());
            assertSame(fallback, shaped.runs().get(0).font());
            assertEquals("Ł", shaped.runs().get(0).text());
            assertSame(chosen, shaped.runs().get(1).font());
            assertEquals("ukasz Weiß", shaped.runs().get(1).text());
            assertTrue(shaped.unprintable().isEmpty());
            assertTrue(shaped.width(10) > 0);
        }
    }

    @Test
    void whatNoFontHasIsLeftOutAndNamedButControlCharactersAreNot() throws IOException {
        try (var document = new PDDocument()) {
            var chain = new StampFonts(DefaultFont.absent()).chainFor(document);

            var shaped = chain.shape("A漢\tB😀");

            assertEquals(Set.of("漢", "😀"), shaped.unprintable());
            assertEquals("AB", shaped.runs().stream().map(FontChain.Run::text).reduce("", String::concat));
        }
    }

    @Test
    void aChainNeedsAFont() {
        assertThrows(IllegalArgumentException.class, () -> new FontChain(List.of()));
    }
}
