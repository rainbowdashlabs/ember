/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public final class CsvParser {
    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private CsvParser() {}

    /**
     * Parses a CSV document, taking its first line as the header.
     *
     * @param content   the whole document, with or without a leading byte order mark
     * @param delimiter the character separating the fields of a record
     * @return the header names and every record below them
     * @throws IOException if the document cannot be read as CSV
     */
    public static ParsedCsv parse(String content, char delimiter) throws IOException {
        var format = CSVFormat.Builder.create()
                .setDelimiter(delimiter)
                .setQuote('"')
                .setRecordSeparator('\n')
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (var parser = CSVParser.parse(new StringReader(stripByteOrderMark(content)), format)) {
            var headers = new ArrayList<>(parser.getHeaderNames());
            var rows = new ArrayList<List<String>>();
            for (var record : parser) {
                var row = new ArrayList<String>();
                for (int i = 0; i < headers.size(); i++) {
                    row.add(i < record.size() ? record.get(i) : "");
                }
                rows.add(row);
            }
            return new ParsedCsv(headers, rows);
        }
    }

    /**
     * Removes a leading byte order mark, which spreadsheet software writes in front of a UTF-8
     * export. Left in place it becomes part of the first header name, so every lookup of that
     * column misses and the import drops it.
     */
    private static String stripByteOrderMark(String content) {
        return !content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK ? content.substring(1) : content;
    }

    public record ParsedCsv(List<String> headers, List<List<String>> rows) {}
}
