/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Words the editor set in a size of their own, stored as {@code <span data-size="24">}, reach Typst as
 * {@code text(size: ...)} in points, a pixel being three quarters of a point, for letters and the wiki export
 * alike. The formatting, fonts and colours around and inside them are converted as anywhere else.
 */
class PandocTypstSizesTest {

    @Test
    void aSizeInPixelsBecomesTheSizeInPoints() throws IOException {
        assertEquals(
                "Ein #text(size: 18pt)[großes] und #text(size: 10.5pt)[kleines] Wort.",
                PandocConverter.markdownToTypst("Ein <span data-size=\"24\">großes</span> und "
                                + "<span data-size=\"14\">kleines</span> Wort.")
                        .strip());
    }

    @Test
    void boldColoursAndFontsInsideASizeAreKept() throws IOException {
        String markdown = "<span data-size=\"32\">**fett** <span data-font=\"Haus\">Schrift</span> "
                + "<span style=\"color: #ff0000\">rot</span></span>";

        assertEquals(
                "#text(size: 24pt)[#strong[fett] #font(\"Haus\")[Schrift] #text(fill: rgb(\"#ff0000\"))[rot]]",
                PandocConverter.markdownToTypstWithFonts(markdown).strip());
    }

    @Test
    void aSizeInsideAnAlignedParagraphIsKept() throws IOException {
        assertEquals(
                "#align(center)[\nEin #text(size: 36pt)[Titel].\n\n]",
                PandocConverter.markdownToTypst(
                                "<div data-align=\"center\">\n\nEin <span data-size=\"48\">Titel</span>.\n\n</div>")
                        .strip());
    }

    /** A size the editor never stores is dropped with its tags, and the words are kept. */
    @ParameterizedTest
    @ValueSource(strings = {"5", "97", "12.5", "12px", ""})
    void aSizeOutOfBoundsKeepsTheWordsOnly(String size) throws IOException {
        assertEquals(
                "Ein Wort.",
                PandocConverter.markdownToTypst("Ein <span data-size=\"" + size + "\">Wort</span>.")
                        .strip());
    }

    /** A size opened and never closed is dropped rather than leaving a bracket open in Typst. */
    @Test
    void anUnclosedSizeIsDropped() throws IOException {
        assertEquals(
                "Ein Wort.",
                PandocConverter.markdownToTypst("Ein <span data-size=\"24\">Wort.")
                        .strip());
    }
}
