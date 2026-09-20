/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.checklist.entity.ChecklistCell;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the matrix export formats for a checklist: CSV for spreadsheet analysis and a
 * landscape PDF for printable sign-off. Both formats include every alive entry; soft-deleted
 * rows are always excluded.
 */
@Singleton
public class ChecklistExportService {
    private static final Logger log = LoggerFactory.getLogger(ChecklistExportService.class);
    private static final DateTimeFormatter CSV_DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter PDF_DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final ChecklistService checklistService;
    private final MemberNameResolver memberNameResolver;
    private final StationRepository stationRepository;
    private final StationLogoService logoService;
    private final Api apiConfig;

    @Inject
    public ChecklistExportService(
            ChecklistService checklistService,
            MemberNameResolver memberNameResolver,
            StationRepository stationRepository,
            StationLogoService logoService,
            Api apiConfig) {
        this.checklistService = checklistService;
        this.memberNameResolver = memberNameResolver;
        this.stationRepository = stationRepository;
        this.logoService = logoService;
        this.apiConfig = apiConfig;
    }

    public ExportedDocument exportCsv(int checklistId, CsvWriter.Separator separator) {
        var checklist = checklistService.findById(checklistId).orElseThrow();
        var columns = checklistService.findColumns(checklistId);
        var entries = checklistService.findEntries(checklistId, false);
        var cells = indexCells(checklistService.findCells(checklistId));
        ZoneId zone = StationFormat.timezoneOf(
                stationRepository.findById(checklist.stationId()).orElse(null));

        var station = stationOf(checklist.stationId());
        String language = StationFormat.languageOf(station);
        boolean english = "en".equals(language);

        var headers = new ArrayList<String>();
        headers.add(DocumentWord.MEMBERS.in(language));
        headers.add(english ? "Updated at" : "Zuletzt geändert");
        for (var column : columns) {
            headers.add(column.label());
            headers.add(column.label() + " " + (english ? "note" : "Notiz"));
        }

        var rows = new ArrayList<List<String>>(entries.size());
        for (var entry : entries) {
            String name = memberNameResolver.official(entry.memberId());
            var cellValues = new ArrayList<String>(headers.size());
            cellValues.add(name != null ? name : "#" + entry.memberId());
            cellValues.add(latestUpdateForEntry(cells, entry.id(), zone));
            for (var column : columns) {
                var cell = cells.get(cellKey(entry.id(), column.id()));
                cellValues.add(yesOrNo(cell != null && cell.checked(), english));
                cellValues.add(cell != null && cell.note() != null ? cell.note() : "");
            }
            rows.add(List.copyOf(cellValues));
        }

        log.info("Exported checklist {} as CSV ({} entries)", checklist.id(), entries.size());
        return ExportedDocument.ofText(
                CsvWriter.write(headers, rows, separator), exportFileName(checklist.name(), station, "csv"));
    }

    /** A ticked box reads as a word rather than a token, in the language the rest of the file is in. */
    private static String yesOrNo(boolean checked, boolean english) {
        if (english) return checked ? "yes" : "no";
        return checked ? "ja" : "nein";
    }

    /** A checklist is named after itself, and dated because it is a snapshot of a moving thing. */
    private String exportFileName(String checklistName, Station station, String extension) {
        String language = StationFormat.languageOf(station);
        return DocumentName.of(
                extension,
                DocumentName.part(checklistName).isEmpty()
                        ? DocumentWord.CHECKLIST.in(language)
                        : DocumentName.part(checklistName),
                DocumentPeriod.day(Instant.now(), StationFormat.timezoneOf(station)));
    }

    private Station stationOf(int stationId) {
        return stationRepository.findById(stationId).orElse(null);
    }

    public ExportedDocument exportPdf(int checklistId, String generatedBy) throws IOException, InterruptedException {
        var checklist = checklistService.findById(checklistId).orElseThrow();
        var station = stationRepository.findById(checklist.stationId()).orElseThrow();
        var columns = checklistService.findColumns(checklistId);
        var entries = checklistService.findEntries(checklistId, false);
        var cells = indexCells(checklistService.findCells(checklistId));

        String locale = StationFormat.languageOf(station);
        ZoneId zone = StationFormat.timezoneOf(station);

        var rows = new ArrayList<Map<String, Object>>();
        for (var entry : entries) {
            String name = memberNameResolver.official(entry.memberId());
            var rowCells = new ArrayList<Map<String, Object>>();
            for (var column : columns) {
                var cell = cells.get(cellKey(entry.id(), column.id()));
                boolean checked = cell != null && cell.checked();
                String note = cell != null && cell.note() != null ? cell.note() : "";
                var cellMap = new LinkedHashMap<String, Object>();
                cellMap.put("checked", checked);
                cellMap.put("note", note);
                rowCells.add(cellMap);
            }
            var row = new LinkedHashMap<String, Object>();
            row.put("name", name != null ? name : "#" + entry.memberId());
            row.put("cells", rowCells);
            rows.add(row);
        }

        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station.name());
        data.put("generatedBy", generatedBy);
        data.put("generatedAt", PDF_DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("showInstanceUrl", StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);
        data.put("checklistName", checklist.name());
        data.put("checklistDescription", checklist.description() == null ? "" : checklist.description());
        data.put("columns", columns.stream().map(ChecklistColumn::label).toList());
        data.put("rows", rows);

        var logo = logoService.original(checklist.stationId()).orElse(null);
        byte[] pdf = TypstCompiler.compileTemplate(
                data,
                locale + "/checklist-export.typ",
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
        log.info(
                "Rendered checklist PDF for {} ({} entries, {} columns)",
                checklist.id(),
                entries.size(),
                columns.size());
        return new ExportedDocument(pdf, exportFileName(checklist.name(), station, "pdf"));
    }

    /**
     * When an entry was last touched, written in the station's own clock.
     *
     * @param zone the station's zone, so a row exported in Berlin does not read two hours early
     */
    private String latestUpdateForEntry(Map<String, ChecklistCell> cells, int entryId, ZoneId zone) {
        return cells.values().stream()
                .filter(c -> c.entryId() == entryId)
                .map(ChecklistCell::updatedAt)
                .max(Comparator.naturalOrder())
                .map(ts -> CSV_DATE_TIME_FMT.format(ts.atZone(zone)))
                .orElse("");
    }

    private Map<String, ChecklistCell> indexCells(List<ChecklistCell> cells) {
        var idx = new HashMap<String, ChecklistCell>();
        for (var cell : cells) {
            idx.put(cellKey(cell.entryId(), cell.columnId()), cell);
        }
        return idx;
    }

    private static String cellKey(int entryId, int columnId) {
        return entryId + ":" + columnId;
    }

    private static String csvField(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return '"' + escaped + '"';
    }
}
