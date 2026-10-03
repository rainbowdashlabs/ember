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
 * Several spaces in a row reach a letter and a wiki PDF as many spaces as were typed. The editor stores every space
 * of a run but the last as a non-breaking space, which Pandoc does not fold and Typst prints as a space ({@code ~}).
 */
class PandocTypstSpacesTest {

    private static final String NBSP = Character.toString(0xa0);

    @Test
    void twoSpacesInARowStayTwo() throws IOException {
        assertEquals("Name:~ Anna", typst("Name:" + NBSP + " Anna"));
    }

    @Test
    void threeSpacesInARowStayThree() throws IOException {
        assertEquals("Name:~~ Anna", typst("Name:" + NBSP + NBSP + " Anna"));
    }

    @Test
    void theSpacesALineStartsWithAreKept() throws IOException {
        assertEquals("~~ eingerückt", typst(NBSP + NBSP + " eingerückt"));
    }

    @Test
    void spacesInsideAColouredSpanAreKept() throws IOException {
        assertEquals(
                "#text(fill: rgb(\"#ff0000\"))[rot~ rot]",
                typst("<span style=\"color: #ff0000\">rot" + NBSP + " rot</span>"));
    }

    @Test
    void singleSpacesStaySingle() throws IOException {
        assertEquals("ein ganz normaler Satz", typst("ein ganz normaler Satz"));
    }

    private static String typst(String markdown) throws IOException {
        return PandocConverter.markdownToTypst(markdown).strip();
    }
}
