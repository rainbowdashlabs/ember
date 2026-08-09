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
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

    public String exportCsv(int checklistId) {
        var checklist = checklistService.findById(checklistId).orElseThrow();
        var columns = checklistService.findColumns(checklistId);
        var entries = checklistService.findEntries(checklistId, false);
        var cells = indexCells(checklistService.findCells(checklistId));

        var sb = new StringBuilder();
        sb.append(csvField("Member")).append(',').append(csvField("Updated at"));
        for (var column : columns) {
            sb.append(',').append(csvField(column.label()));
            sb.append(',').append(csvField(column.label() + " — Note"));
        }
        sb.append('\n');

        for (var entry : entries) {
            String name = memberNameResolver.resolveLocal(entry.memberId());
            sb.append(csvField(name != null ? name : "#" + entry.memberId()));
            var latestUpdate = latestUpdateForEntry(cells, entry.id());
            sb.append(',').append(csvField(latestUpdate));
            for (var column : columns) {
                var cell = cells.get(cellKey(entry.id(), column.id()));
                String checkedValue = cell != null && cell.checked() ? "yes" : "no";
                String noteValue = cell != null && cell.note() != null ? cell.note() : "";
                sb.append(',').append(csvField(checkedValue));
                sb.append(',').append(csvField(noteValue));
            }
            sb.append('\n');
        }
        log.info("Exported checklist {} as CSV ({} entries)", checklist.id(), entries.size());
        return sb.toString();
    }

    public byte[] exportPdf(int checklistId, String generatedBy) throws IOException, InterruptedException {
        var checklist = checklistService.findById(checklistId).orElseThrow();
        var station = stationRepository.findById(checklist.stationId()).orElseThrow();
        var columns = checklistService.findColumns(checklistId);
        var entries = checklistService.findEntries(checklistId, false);
        var cells = indexCells(checklistService.findCells(checklistId));

        String locale = resolveLocalePrefix(station);
        ZoneId zone = resolveTimezone(station);

        var rows = new ArrayList<Map<String, Object>>();
        for (var entry : entries) {
            String name = memberNameResolver.resolveLocal(entry.memberId());
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
        return pdf;
    }

    private String latestUpdateForEntry(Map<String, ChecklistCell> cells, int entryId) {
        return cells.values().stream()
                .filter(c -> c.entryId() == entryId)
                .map(ChecklistCell::updatedAt)
                .max(Comparator.naturalOrder())
                .map(ts -> CSV_DATE_TIME_FMT.format(ts.atZone(ZoneOffset.UTC)))
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

    private static String resolveLocalePrefix(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("de")) return "de";
        return "en";
    }

    private static ZoneId resolveTimezone(Station station) {
        if (station != null && station.timezone() != null) {
            try {
                return ZoneId.of(station.timezone());
            } catch (Exception ignored) {
            }
        }
        return ZoneOffset.UTC;
    }
}
