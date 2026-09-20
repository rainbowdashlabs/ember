/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one way a spreadsheet is written, now that there is only one.
 *
 * <p>What matters is that a cell arrives as the cell it was: a name with a comma in it must not
 * become two columns, and a note written over two lines must not become two rows.
 */
class CsvWriterTest {

    @Test
    void headersAndRowsAreWrittenInOrder() {
        String csv = CsvWriter.write(
                List.of("Name", "Stunden"),
                List.of(List.of("Anna", "12"), List.of("Bert", "8")),
                CsvWriter.Separator.SEMICOLON);

        assertEquals("Name;Stunden\nAnna;12\nBert;8\n", csv);
    }

    @Test
    void theReaderChoosesTheSeparator() {
        String csv = CsvWriter.write(List.of("A", "B"), List.of(List.of("1", "2")), CsvWriter.Separator.COMMA);

        assertEquals("A,B\n1,2\n", csv);
    }

    /** A cell holding the separator would otherwise become two columns. */
    @Test
    void aCellMayHoldTheSeparatorItself() {
        String csv = CsvWriter.write(
                List.of("Name"), List.of(List.of("Müller; Anna")), CsvWriter.Separator.SEMICOLON);

        assertEquals("Name\n\"Müller; Anna\"\n", csv);
    }

    @Test
    void aQuoteInsideACellIsDoubled() {
        String csv = CsvWriter.write(List.of("Name"), List.of(List.of("Anna \"Ani\"")), CsvWriter.Separator.COMMA);

        assertEquals("Name\n\"Anna \"\"Ani\"\"\"\n", csv);
    }

    /** A short row still has to line up with the header, or the columns shift from there on. */
    @Test
    void aRowShorterThanTheHeaderIsFilledOut() {
        String csv = CsvWriter.write(
                List.of("A", "B", "C"), List.of(List.of("1")), CsvWriter.Separator.SEMICOLON);

        assertEquals("A;B;C\n1;;\n", csv);
    }

    @Test
    void aMissingCellIsAnEmptyOneRatherThanTheWordNull() {
        String csv = CsvWriter.write(
                List.of("A", "B"), List.of(Arrays.asList("1", null)), CsvWriter.Separator.SEMICOLON);

        assertEquals("A;B\n1;\n", csv);
    }

    /** A cell padded with spaces would come back trimmed, which quietly changes what it said. */
    @Test
    void spaceAtTheEdgeOfACellIsKept() {
        String csv = CsvWriter.write(List.of("A"), List.of(List.of(" 12 ")), CsvWriter.Separator.SEMICOLON);

        assertEquals("A\n\" 12 \"\n", csv);
    }

    /** A note written over two lines must not become two rows. */
    @Test
    void aLineBreakInsideACellIsQuoted() {
        String csv = CsvWriter.write(List.of("A"), List.of(List.of("eins\nzwei")), CsvWriter.Separator.SEMICOLON);

        assertEquals("A\n\"eins\nzwei\"\n", csv);
    }

    @Test
    void whatTheRequestAskedForDecidesTheSeparator() {
        assertEquals(CsvWriter.Separator.COMMA, CsvWriter.Separator.of("comma"));
        assertEquals(CsvWriter.Separator.SEMICOLON, CsvWriter.Separator.of("semicolon"));
    }

    /** Asking for nothing, or for something nobody offers, gets the one German spreadsheets expect. */
    @Test
    void theSemicolonIsWhatAnUnaskedQuestionAnswers() {
        assertEquals(CsvWriter.Separator.SEMICOLON, CsvWriter.Separator.of(null));
        assertEquals(CsvWriter.Separator.SEMICOLON, CsvWriter.Separator.of("tab"));
    }
}
