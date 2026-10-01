/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes every spreadsheet export: Commons CSV with bare line feeds and minimal quoting, empty cells as
 * nothing, short rows padded to the header's width, and formula starts defused.
 */
public final class CsvWriter {

    /** What goes between two cells; the reader picks it, since the wrong one puts a row into a single column. */
    public enum Separator {
        SEMICOLON(';'),
        COMMA(',');

        private final CSVFormat format;

        Separator(char character) {
            this.format = CSVFormat.Builder.create()
                    .setDelimiter(character)
                    .setRecordSeparator('\n')
                    .setQuoteMode(QuoteMode.MINIMAL)
                    .get();
        }

        /** The comma when asked for, otherwise the semicolon German spreadsheets expect. */
        public static Separator of(@Nullable String asked) {
            return "comma".equalsIgnoreCase(asked) ? COMMA : SEMICOLON;
        }
    }

    private static final String FORMULA_STARTS = "=+-@\t\r";

    private CsvWriter() {}

    /**
     * Writes a header line and its rows.
     *
     * @param headers   the column titles, already in the station's language
     * @param rows      the rows, in output order
     * @param separator what goes between two cells
     */
    public static String write(List<String> headers, List<List<String>> rows, Separator separator) {
        var out = new StringBuilder();
        try (var printer = new CSVPrinter(out, separator.format)) {
            printer.printRecord(line(headers, headers.size()));
            for (var row : rows) {
                printer.printRecord(line(row, headers.size()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Writing to memory failed", e);
        }
        return out.toString();
    }

    private static List<String> line(List<String> cells, int width) {
        var line = new ArrayList<String>(width);
        for (int i = 0; i < width; i++) {
            line.add(defused(i < cells.size() ? cells.get(i) : null));
        }
        return line;
    }

    /**
     * Puts an apostrophe before a cell a spreadsheet would run as a formula, so a typed value cannot reach
     * into the sheet or make the machine fetch something. Empty becomes {@code null}, which prints as nothing.
     */
    private static @Nullable String defused(@Nullable String value) {
        if (value == null || value.isEmpty()) return null;
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
