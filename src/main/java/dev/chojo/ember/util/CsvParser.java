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
    private CsvParser() {}

    public record ParsedCsv(List<String> headers, List<List<String>> rows) {}

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

        try (var parser = CSVParser.parse(new StringReader(content), format)) {
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
}
