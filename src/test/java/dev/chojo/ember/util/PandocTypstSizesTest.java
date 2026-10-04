/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Words the editor set in a size of their own, stored as {@code <span style="font-size: 24px">}, reach Typst as
 * {@code text(size: ...)} in points, a pixel being three quarters of a point, for letters and the wiki export
 * alike. The formatting, fonts and colours around and inside them are converted as anywhere else.
 */
class PandocTypstSizesTest {

    @Test
    void aSizeInPixelsBecomesTheSizeInPoints() throws IOException {
        assertEquals(
                "Ein #text(size: 18pt)[großes] und #text(size: 10.5pt)[kleines] Wort.",
                PandocConverter.markdownToTypst("Ein <span style=\"font-size: 24px\">großes</span> und "
                                + "<span style=\"font-size: 14px\">kleines</span> Wort.")
                        .strip());
    }

    @Test
    void boldColoursAndFontsInsideASizeAreKept() throws IOException {
        String markdown = "<span style=\"font-size: 32px\">**fett** <span data-font=\"Haus\">Schrift</span> "
                + "<span style=\"color: #ff0000\">rot</span></span>";

        assertEquals(
                "#text(size: 24pt)[#strong[fett] #font(\"Haus\")[Schrift] #text(fill: rgb(\"#ff0000\"))[rot]]",
                PandocConverter.markdownToTypstWithFonts(markdown).strip());
    }

    /** A span that sets a colour and a size prints in both, neither taking the place of the other. */
    @Test
    void aColourAndASizeOfOneSpanAreBothKept() throws IOException {
        assertEquals(
                "Ein #text(size: 18pt, fill: rgb(\"#ff0000\"))[Wort].",
                PandocConverter.markdownToTypst("Ein <span style=\"color: #ff0000; font-size: 24px\">Wort</span>.")
                        .strip());
    }

    @Test
    void aSizeInsideAnAlignedParagraphIsKept() throws IOException {
        assertEquals(
                "#align(center)[\nEin #text(size: 36pt)[Titel].\n\n]",
                PandocConverter.markdownToTypst(
                                "<div data-align=\"center\">\n\nEin <span style=\"font-size: 48px\">Titel</span>.\n\n</div>")
                        .strip());
    }

    /** A size beyond what the editor offers is held to the smallest or the largest it offers. */
    @ParameterizedTest
    @CsvSource({"5, 4.5pt", "0, 4.5pt", "97, 72pt", "400, 72pt"})
    void aSizeOutOfBoundsIsHeldToTheBounds(String size, String points) throws IOException {
        assertEquals(
                "Ein #text(size: " + points + ")[Wort].",
                PandocConverter.markdownToTypst("Ein <span style=\"font-size: " + size + "px\">Wort</span>.")
                        .strip());
    }

    /** A size the editor never writes, in anything but whole pixels, is dropped with its tags, and the words are kept. */
    @ParameterizedTest
    @ValueSource(strings = {"12.5px", "12", "1em", "12pt", ""})
    void aSizeNotInWholePixelsKeepsTheWordsOnly(String size) throws IOException {
        assertEquals(
                "Ein Wort.",
                PandocConverter.markdownToTypst("Ein <span style=\"font-size: " + size + "\">Wort</span>.")
                        .strip());
    }

    /** A size opened and never closed is dropped rather than leaving a bracket open in Typst. */
    @Test
    void anUnclosedSizeIsDropped() throws IOException {
        assertEquals(
                "Ein Wort.",
                PandocConverter.markdownToTypst("Ein <span style=\"font-size: 24px\">Wort.")
                        .strip());
    }
}
