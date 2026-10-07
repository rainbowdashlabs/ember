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
 * Words a letter sets in a family of their own reach Typst as a call to the letter's {@code font}
 * function, with the bold and the colour inside them kept; every other conversion drops the span and
 * keeps the words, since nothing else defines that function.
 */
class PandocTypstFontsTest {
    private static final String TEXT =
            "Ein <span data-font=\"Berlin &amp; &quot;Type&quot;\">**fettes** und <span style=\"color: #ff0000\">rotes</span></span> Wort.";

    @Test
    void aFontSpanBecomesACallToTheLettersFontFunction() throws IOException {
        assertEquals(
                "Ein #font(\"Berlin & \\\"Type\\\"\")[#strong[fettes] und #text(fill: rgb(\"#ff0000\"))[rotes]] Wort.",
                PandocConverter.markdownToTypstWithFonts(TEXT).strip());
    }

    @Test
    void elsewhereTheSpanIsDroppedAndItsWordsKept() throws IOException {
        assertEquals(
                "Ein #strong[fettes] und #text(fill: rgb(\"#ff0000\"))[rotes] Wort.",
                PandocConverter.markdownToTypst(TEXT).strip());
    }
}
