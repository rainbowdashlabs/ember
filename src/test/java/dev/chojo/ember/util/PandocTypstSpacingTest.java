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
 * Words the editor set closer or further apart, stored as {@code <span data-spacing="wide">}, reach Typst
 * with the matching tracking. A paragraph's line height rides on the block wrapper its alignment uses, as
 * {@code data-line-height}, and reaches Typst as the leading of a block scoped to it, with the alignment set
 * inside the same block.
 */
class PandocTypstSpacingTest {

    @ParameterizedTest
    @CsvSource({"tight,-0.03em", "wide,0.05em", "wider,0.1em", "widest,0.2em"})
    void spacedWordsGetTheirTracking(String spacing, String tracking) throws IOException {
        assertEquals(
                "Ein #text(tracking: " + tracking + ")[weites] Wort.",
                PandocConverter.markdownToTypst("Ein <span data-spacing=\"" + spacing + "\">weites</span> Wort.")
                        .strip());
    }

    @Test
    void aSpacingTheEditorNeverWritesKeepsTheWordsOnly() throws IOException {
        assertEquals(
                "Ein weites Wort.",
                PandocConverter.markdownToTypst("Ein <span data-spacing=\"huge\">weites</span> Wort.")
                        .strip());
    }

    @ParameterizedTest
    @CsvSource({"1,0.05em", "1.15,0.2em", "1.5,0.55em", "2,1.05em"})
    void aLineHeightBecomesTheLeadingOfItsBlock(String lineHeight, String leading) throws IOException {
        assertEquals(
                "#[#set par(leading: " + leading + ")\nEin Absatz.\n\n]",
                PandocConverter.markdownToTypst(
                                "<div data-line-height=\"" + lineHeight + "\">\n\nEin Absatz.\n\n</div>")
                        .strip());
    }

    @Test
    void aLineHeightAndAnAlignmentShareOneBlock() throws IOException {
        assertEquals(
                "#[#set par(leading: 0.55em)\n#set align(center)\nEin Absatz.\n\n]",
                PandocConverter.markdownToTypst(
                                "<div data-align=\"center\" data-line-height=\"1.5\">\n\nEin Absatz.\n\n</div>")
                        .strip());
    }
}
