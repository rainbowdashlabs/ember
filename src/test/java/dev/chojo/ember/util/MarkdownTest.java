/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkdownTest {

    @Test
    void headingsEmphasisAndTablesAreRendered() {
        String html = Markdown.toHtml("# Titel\n\n**fett**\n\n| a | b |\n|---|---|\n| 1 | 2 |");
        assertTrue(html.contains("<h1"));
        assertTrue(html.contains("<strong>fett</strong>"));
        assertTrue(html.contains("<table"));
    }

    @Test
    void nothingRendersToNothingRatherThanAnEmptyDocument() {
        assertEquals("", Markdown.toHtml(null));
        assertEquals("", Markdown.toHtml("   "));
    }

    @Test
    void scriptsDoNotSurviveTheRender() {
        String html = Markdown.toHtml("<script>alert(1)</script>\n\nDanach");
        assertFalse(html.contains("<script"));
        assertTrue(html.contains("Danach"));
    }
}
