/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import dev.chojo.ember.util.HtmlSanitizer.Policy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownTest {

    @Test
    void headingsEmphasisAndTablesAreRendered() {
        String html = Markdown.toHtml("# Titel\n\n**fett**\n\n| a | b |\n|---|---|\n| 1 | 2 |", Policy.RICH);
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("<strong>fett</strong>"));
        assertTrue(html.contains("<table"));
    }

    @Test
    void nothingRendersToNothingRatherThanAnEmptyDocument() {
        assertEquals("", Markdown.toHtml(null, Policy.RICH));
        assertEquals("", Markdown.toHtml("   ", Policy.STRICT));
    }

    @Test
    void scriptsDoNotSurviveTheRender() {
        String html = Markdown.toHtml("<script>alert(1)</script>\n\nDanach", Policy.RICH);
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("Danach"));
    }

    @Test
    void theStrictPolicyDropsImages() {
        String markdown = "![Bild](/api/v1/media/abc)";
        assertFalse(Markdown.toHtml(markdown, Policy.STRICT).contains("<img"));
    }

    @Test
    void plainTextKeepsTheWordsAndDropsTheMarkup() {
        String text = Markdown.toPlainText(
                "# Überschrift\n\n**fett** und _kursiv_ mit [einem Link](/ziel) und `code`\n\n> zitiert\n\n---\n\n~~weg~~");

        assertEquals("Überschrift\n\nfett und kursiv mit einem Link und code\n\nzitiert\n\nweg", text);
    }

    @Test
    void anImageAndInlineHtmlLeaveNoWords() {
        assertEquals("vor nach", Markdown.toPlainText("vor ![alt](/bild.png)<b></b>nach"));
    }

    @Test
    void anHtmlBlockKeepsTheTextBetweenItsTags() {
        assertEquals("Zumischer kalibrieren", Markdown.toPlainText("<div><p>Zumischer kalibrieren</p></div>"));
    }

    @Test
    void listsKeepABulletOrTheirNumber() {
        assertEquals("• eins\n• zwei", Markdown.toPlainText("- eins\n- zwei"));
        assertEquals("3. drei\n4. vier", Markdown.toPlainText("3. drei\n4. vier"));
    }

    @Test
    void tableCellsAreJoinedWithAMiddleDot() {
        assertEquals("a · b\n1 · 2", Markdown.toPlainText("| a | b |\n|---|---|\n| 1 | 2 |"));
    }

    @Test
    void blankMarkdownHasNoPlainText() {
        assertEquals("", Markdown.toPlainText(null));
        assertEquals("", Markdown.toPlainText("  \n "));
    }
}
