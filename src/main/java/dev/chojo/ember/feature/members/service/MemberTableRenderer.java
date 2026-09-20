/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.MemberTableColumnKind;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands a drawn table over as a file, as a sheet to carry and as a table to work with.
 *
 * <p>Both come from the same {@link MemberTable}, which has already been cut to what the reader may
 * see. Rendering from anything else would let the two disagree about that, and the one that
 * disagreed would be the one somebody mailed onwards.
 */
@Singleton
public class MemberTableRenderer {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public MemberTableRenderer(StationRepository stationRepository, Api apiConfig) {
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    /**
     * The table as a spreadsheet reads it.
     *
     * @param table     the drawn table
     * @param station   the station it belongs to, which decides the language its values are worded in
     * @param separator what the reader asked for between the cells
     * @return the whole file
     */
    public String toCsv(MemberTable table, Station station, CsvWriter.Separator separator) {
        var language = StationFormat.languageOf(station);
        var headers = table.columns().stream()
                .map(MemberTable.MemberTableHeader::label)
                .toList();
        var rows = table.rows().stream()
                .map(row -> {
                    var cells = new ArrayList<String>(row.values().size());
                    for (int i = 0; i < row.values().size(); i++) {
                        cells.add(worded(table.columns().get(i), row.values().get(i), language));
                    }
                    return List.copyOf(cells);
                })
                .toList();
        return CsvWriter.write(headers, rows, separator);
    }

    /**
     * The table as a sheet somebody carries.
     *
     * @param table       the drawn table
     * @param station     the station it belongs to, which decides the language and the letterhead
     * @param title       what the sheet is called
     * @param subtitle    a line under the title saying which people these are, or empty
     * @param generatedBy who asked for it
     * @return the rendered document
     * @throws Exception where the renderer refuses the document
     */
    public byte[] toPdf(MemberTable table, Station station, String title, String subtitle, String generatedBy)
            throws Exception {
        var language = StationFormat.languageOf(station);
        var rows = new ArrayList<Map<String, Object>>();
        for (var row : table.rows()) {
            var worded = new ArrayList<String>(row.values().size());
            for (int i = 0; i < row.values().size(); i++) {
                worded.add(worded(table.columns().get(i), row.values().get(i), language));
            }
            rows.add(Map.of("values", worded));
        }

        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station.name() == null ? "" : station.name());
        data.put("generatedBy", generatedBy == null ? "" : generatedBy);
        data.put("generatedAt", STAMP.format(Instant.now().atZone(StationFormat.timezoneOf(station))));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("showInstanceUrl", StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);
        data.put("title", title);
        data.put("subtitle", subtitle == null ? "" : subtitle);
        data.put(
                "columns",
                table.columns().stream()
                        .map(MemberTable.MemberTableHeader::label)
                        .toList());
        data.put("rows", rows);

        var logo = stationRepository.findLogo(station.id()).orElse(null);
        return TypstCompiler.compileTemplate(
                data,
                StationFormat.languageOf(station) + "/member-table.typ",
                logo == null ? null : new TypstCompiler.StationLogo(logo.data(), logo.contentType()));
    }

    /**
     * A cell as a file should read it, which is words where the table holds a token.
     *
     * <p>The drawn table carries what a thing is rather than what it is called, because the screen has
     * the words for it already and two places holding the same wording is one too many. A file has no
     * screen behind it, so the words are put in here and only here.
     */
    private String worded(MemberTable.MemberTableHeader column, String value, String language) {
        if (value == null || value.isBlank() || column.kind() != MemberTableColumnKind.BUILTIN) return value;
        boolean english = "en".equals(language);
        return switch (column.key()) {
            case "registrationStatus" ->
                switch (value) {
                    case "ACCEPTED" -> english ? "Confirmed" : "Bestätigt";
                    case "PENDING" -> english ? "Pending" : "Ausstehend";
                    case "DENIED" -> english ? "Turned down" : "Abgelehnt";
                    case "DECLINED" -> english ? "Declined" : "Abgemeldet";
                    case "WITHDRAWN" -> english ? "Withdrawn" : "Zurückgezogen";
                    default -> value;
                };
            case "memberType" ->
                switch (value) {
                    case "TRIAL" -> english ? "Trial" : "Probe";
                    case "MEMBER" -> english ? "Member" : "Mitglied";
                    case "GUARDIAN" -> english ? "Guardian" : "Erziehungsberechtigter";
                    case "TEAM" -> "Team";
                    case "MANAGER" -> "Manager";
                    default -> value;
                };
            default -> value;
        };
    }

    /**
     * One cell, safe to hand to a spreadsheet.
     *
     * <p>A cell is quoted whenever it holds a separator, a quote or a line break, and a leading
     * equals, plus or minus is pushed behind a quote: a spreadsheet reads those as the start of a
     * formula, and a name is not a formula however it begins.
     */
    private String cell(String value) {
        if (value == null || value.isEmpty()) return "";
        var safe = value.startsWith("=") || value.startsWith("+") || value.startsWith("-") || value.startsWith("@")
                ? "'" + value
                : value;
        if (safe.contains(";") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    /** The columns as a screen names them, which is what a picker shows beside each one. */
    public List<String> headerLabels(MemberTable table) {
        return table.columns().stream()
                .map(MemberTable.MemberTableHeader::label)
                .toList();
    }
}
