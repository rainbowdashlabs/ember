/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.TestFonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Which families a letter asks Typst for where its page names none. */
class LetterFontsTest {

    private static LetterFonts fontsOf(LetterPage page, DefaultFont defaultFont) {
        return LetterFonts.of(
                ReachableFonts.none(),
                new LetterContent(List.of(), List.of(), List.of(), page),
                font -> Optional.empty(),
                defaultFont);
    }

    /** Without a default font every part prints in Liberation Sans, exactly as before. */
    @Test
    void withoutADefaultFontEveryPartPrintsInLiberationSans() {
        var fonts = fontsOf(LetterPage.defaults(), DefaultFont.absent());

        var liberation = List.of(BundledFont.FAMILY);
        assertEquals(Map.of("body", liberation, "header", liberation, "footer", liberation), fonts.families());
        assertEquals(List.of(), fonts.directories());
        assertEquals(List.of(), fonts.uprightFamilies());
        assertEquals(List.of(BundledFont.FILE_NAME), List.copyOf(fonts.files().keySet()));
    }

    /**
     * The default font comes first, Liberation Sans behind it; its directory is searched, and lacking an
     * italic, its italic text goes on to Liberation Sans.
     */
    @Test
    void aPartNamingNoFamilyPrintsInTheDefaultFont(@TempDir Path directory) {
        var fonts = fontsOf(LetterPage.defaults(), TestFonts.defaultFontIn(directory));

        var chain = List.of(TestFonts.LISU_FAMILY, BundledFont.FAMILY);
        assertEquals(Map.of("body", chain, "header", chain, "footer", chain), fonts.families());
        assertEquals(List.of(directory.toAbsolutePath()), fonts.directories());
        assertEquals(List.of(TestFonts.LISU_FAMILY), fonts.uprightFamilies());
    }

    /** A family the station no longer reaches prints in the default font, as one naming none would. */
    @Test
    void aFamilyThatIsGonePrintsInTheDefaultFont(@TempDir Path directory) {
        var page = new LetterPage(40, 30, 20, 20, 10, "Gibt es nicht mehr", null, null);
        var fonts = fontsOf(page, TestFonts.defaultFontIn(directory));

        assertEquals(
                List.of(TestFonts.LISU_FAMILY, BundledFont.FAMILY),
                fonts.families().get("body"));
    }
}
