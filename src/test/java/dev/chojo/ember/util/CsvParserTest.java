/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvParserTest {
    private static final String BOM = String.valueOf((char) 0xFEFF);

    @Test
    void parsesHeaderAndRows() throws IOException {
        var parsed = CsvParser.parse("first;last\nAnna;Smith\n", ';');

        assertEquals(List.of("first", "last"), parsed.headers());
        assertEquals(List.of(List.of("Anna", "Smith")), parsed.rows());
    }

    @Test
    void stripsLeadingByteOrderMark() throws IOException {
        var parsed = CsvParser.parse(BOM + "first;last\nAnna;Smith\n", ';');

        assertEquals(List.of("first", "last"), parsed.headers());
        assertEquals(List.of(List.of("Anna", "Smith")), parsed.rows());
    }

    @Test
    void keepsByteOrderMarkAppearingLater() throws IOException {
        var parsed = CsvParser.parse("first;last\nAnna;" + BOM + "Smith\n", ';');

        assertEquals(List.of(List.of("Anna", BOM + "Smith")), parsed.rows());
    }

    @Test
    void parsesUmlautsFromUtf8Bytes() throws IOException {
        byte[] export = (BOM + "first;last\nJörg;Müller\n").getBytes(StandardCharsets.UTF_8);

        var parsed = CsvParser.parse(new String(export, StandardCharsets.UTF_8), ';');

        assertEquals(List.of("first", "last"), parsed.headers());
        assertEquals(List.of(List.of("Jörg", "Müller")), parsed.rows());
    }

    @Test
    void handlesEmptyContent() throws IOException {
        var parsed = CsvParser.parse("", ';');

        assertTrue(parsed.headers().isEmpty());
        assertTrue(parsed.rows().isEmpty());
    }
}
