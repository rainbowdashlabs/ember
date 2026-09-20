/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import java.util.List;

/**
 * The one way a spreadsheet is written here.
 *
 * <p>There used to be four, agreeing on nothing: two separators, two opinions about the byte order
 * mark, and headers in the station's language in one and hardcoded English in another. A reader
 * opening two exports from the same product got two different files.
 *
 * <p>A cell is quoted where it needs to be and left alone where it does not, which keeps a file
 * readable to a person opening it in anything but a spreadsheet. What needs it is a cell holding the
 * separator, a quote, a line break, or space at either end that would otherwise be eaten.
 */
public final class CsvWriter {

    /**
     * What goes between two cells.
     *
     * <p>Offered to the reader rather than decided here: a spreadsheet set up for German expects the
     * semicolon, and something being fed the file afterwards often insists on the comma. Guessing
     * wrong puts every row into a single column, which is the failure people report as "the export is
     * broken".
     */
    public enum Separator {
        SEMICOLON(';'),
        COMMA(',');

        private final char character;

        Separator(char character) {
            this.character = character;
        }

        /**
         * Reads a separator from what the request asked for, falling back to the semicolon.
         *
         * <p>The semicolon is the default because this is written for German spreadsheets first, and
         * because a comma file opened there needs the reader to know about import dialogs.
         */
        public static Separator of(String asked) {
            return "comma".equalsIgnoreCase(asked) ? COMMA : SEMICOLON;
        }
    }

    private CsvWriter() {}

    /**
     * Writes a header line and its rows.
     *
     * <p>A row shorter than the header is filled out rather than cut short, so every line has the same
     * number of cells and the columns stay lined up.
     *
     * @param headers   the column titles, already in the station's language
     * @param rows      the rows, in output order
     * @param separator what goes between two cells
     */
    public static String write(List<String> headers, List<List<String>> rows, Separator separator) {
        var out = new StringBuilder();
        line(out, headers, headers.size(), separator);
        for (var row : rows) {
            line(out, row, headers.size(), separator);
        }
        return out.toString();
    }

    private static void line(StringBuilder out, List<String> cells, int width, Separator separator) {
        for (int i = 0; i < width; i++) {
            if (i > 0) out.append(separator.character);
            out.append(cell(i < cells.size() ? cells.get(i) : null, separator));
        }
        out.append('\n');
    }

    /** A doubled quote is how a quote is written inside a quoted cell, which is all the escaping there is. */
    private static String cell(String rawValue, Separator separator) {
        if (rawValue == null || rawValue.isEmpty()) return "";
        String value = defused(rawValue);
        if (!needsQuoting(value, separator)) return value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Stops a cell being read as a formula when the file is opened.
     *
     * <p>A spreadsheet runs what a cell says where it begins like this, so a value somebody typed into
     * a form can reach for what is in the rest of the sheet, or ask the machine opening it to fetch
     * something. An apostrophe in front makes it text again, and is what spreadsheets themselves put
     * there for the same reason.
     */
    private static String defused(String value) {
        return FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }

    private static final String FORMULA_STARTS = "=+-@\t\r";

    private static boolean needsQuoting(String value, Separator separator) {
        return value.indexOf(separator.character) >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0
                || !value.equals(value.strip());
    }
}
