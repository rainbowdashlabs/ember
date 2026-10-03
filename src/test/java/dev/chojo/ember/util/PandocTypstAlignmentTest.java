/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A paragraph or heading the editor aligned is stored wrapped in {@code <div data-align="...">}, and reaches
 * Typst as the matching block around its converted markdown: centred and right-aligned through
 * {@code align}, justified through a paragraph setting scoped to the block. The words, the formatting and the
 * fonts inside it are converted as anywhere else, for letters and the wiki export alike.
 */
class PandocTypstAlignmentTest {

    private static String aligned(String alignment, String markdown) {
        return "<div data-align=\"" + alignment + "\">\n\n" + markdown + "\n\n</div>";
    }

    @ParameterizedTest
    @CsvSource({"center,#align(center)[", "right,#align(right)[", "justify,#[#set par(justify: true)"})
    void anAlignedParagraphBecomesTheMatchingBlock(String alignment, String opening) throws IOException {
        assertEquals(
                opening + "\nEin #strong[fettes] Wort.\n\n]",
                PandocConverter.markdownToTypst(aligned(alignment, "Ein **fettes** Wort."))
                        .strip());
    }

    @Test
    void anAlignedHeadingKeepsItsLevel() throws IOException {
        assertEquals(
                "#align(right)[\n== Titel\n<titel>\n]",
                PandocConverter.markdownToTypst(aligned("right", "## Titel")).strip());
    }

    @Test
    void fontsAndColoursInsideAnAlignedParagraphAreKept() throws IOException {
        String markdown = aligned(
                "center", "Ein <span data-font=\"Haus\">Wort</span> und <span style=\"color: #ff0000\">rot</span>.");

        assertEquals(
                "#align(center)[\nEin #font(\"Haus\")[Wort] und #text(fill: rgb(\"#ff0000\"))[rot].\n\n]",
                PandocConverter.markdownToTypstWithFonts(markdown).strip());
    }

    @Test
    void anUnalignedParagraphStaysPlain() throws IOException {
        assertEquals(
                "Ein Absatz.", PandocConverter.markdownToTypst("Ein Absatz.").strip());
    }

    /** An alignment the editor never writes is dropped with its wrapper, and the words are kept. */
    @Test
    void anUnknownAlignmentKeepsTheWordsOnly() throws IOException {
        assertEquals(
                "Ein Absatz.",
                PandocConverter.markdownToTypst(aligned("left", "Ein Absatz.")).strip());
        assertEquals(
                "Ein Absatz.",
                PandocConverter.markdownToTypst(aligned("middle", "Ein Absatz."))
                        .strip());
    }

    /** A wrapper opened and never closed is dropped rather than leaving a bracket open in Typst. */
    @Test
    void anUnclosedWrapperIsDropped() throws IOException {
        assertEquals(
                "Ein Absatz.",
                PandocConverter.markdownToTypst("<div data-align=\"center\">\n\nEin Absatz.")
                        .strip());
    }
}
