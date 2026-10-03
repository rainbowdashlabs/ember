/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Any colour the editor stores, not only one of its swatches, reaches Typst as the same colour: text
 * colour as {@code text(fill: ...)}, a highlight as {@code highlight(fill: ...)}, written as a hex code of
 * three or six digits or as the {@code rgb()} earlier versions stored.
 */
class PandocTypstColoursTest {

    @Test
    void anyTextColourIsKept() throws IOException {
        assertEquals(
                "Ein #text(fill: rgb(\"#1a2b3c\"))[Wort] und #text(fill: rgb(\"#abc\"))[noch eins].",
                PandocConverter.markdownToTypst("Ein <span style=\"color: #1a2b3c\">Wort</span> und "
                                + "<span style=\"color: #abc\">noch eins</span>.")
                        .strip());
    }

    @Test
    void anyHighlightColourIsKept() throws IOException {
        assertEquals(
                "Ein #highlight(fill: rgb(\"#a1b2c3\"))[Wort].",
                PandocConverter.markdownToTypst(
                                "Ein <mark data-color=\"#a1b2c3\" style=\"background-color: #a1b2c3\">Wort</mark>.")
                        .strip());
    }

    @Test
    void aColourStoredAsRgbIsKept() throws IOException {
        assertEquals(
                "#text(fill: rgb(26, 43, 60))[Wort]",
                PandocConverter.markdownToTypst("<span style=\"color: rgb(26, 43, 60)\">Wort</span>")
                        .strip());
    }
}
