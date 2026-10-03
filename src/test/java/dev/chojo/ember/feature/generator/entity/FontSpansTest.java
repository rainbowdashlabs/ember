/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The families a text of a letter sets words in, read the way the editor writes them. */
class FontSpansTest {
    @Test
    void everySpanNamesItsFamilyUnescaped() {
        String text = "<span data-font=\"Berlin Type\">a</span> <span style=\"color: red\">b</span> "
                + "<span data-font=\" Fett &amp; &quot;Breit&quot; \">c</span> <span data-font=\"\">d</span> "
                + "<span data-font=\"Berlin Type\">e</span>";

        assertEquals(
                List.of("Berlin Type", "Fett & \"Breit\"", "Berlin Type"),
                FontSpans.familiesIn(text).toList());
    }

    @Test
    void aLetterNamesThePageFamiliesThenThoseOfItsTexts() {
        var cell = new ContentCell(
                0, 0, 0, 100, CellContentType.MARKDOWN, "<span data-font=\"Wort\">x</span>", CellConfig.EMPTY);
        var letter = new LetterContent(
                List.of(),
                List.of(),
                List.of(new ContentRow(0, 0, 0, List.of(cell))),
                new LetterPage(40, 30, 20, 20, 10, "Seite", null, null));

        assertEquals(List.of("Seite", "Wort"), letter.fontFamilies().toList());
    }
}
