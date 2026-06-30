/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.feature.checklist.entity.Checklist;
import dev.chojo.ember.feature.checklist.entity.ChecklistCell;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ChecklistService checklistService;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public ChecklistExportService(ChecklistService checklistService, MemberNameResolver memberNameResolver) {
        this.checklistService = checklistService;
        this.memberNameResolver = memberNameResolver;
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

    public byte[] exportPdf(int checklistId) throws IOException, InterruptedException {
        var checklist = checklistService.findById(checklistId).orElseThrow();
        var columns = checklistService.findColumns(checklistId);
        var entries = checklistService.findEntries(checklistId, false);
        var cells = indexCells(checklistService.findCells(checklistId));
        String typst = renderTypst(checklist, columns, entries, cells);
        return TypstCompiler.compile(typst, Map.of());
    }

    private String renderTypst(
            Checklist checklist,
            List<ChecklistColumn> columns,
            List<ChecklistEntry> entries,
            Map<String, ChecklistCell> cells) {
        var sb = new StringBuilder();
        sb.append("#set page(paper: \"a4\", flipped: true, margin: 1.5cm)\n");
        sb.append("#set text(font: \"Linux Libertine\", size: 9pt)\n");
        sb.append("#align(center)[= ").append(typstEscape(checklist.name())).append("]\n\n");
        if (checklist.description() != null && !checklist.description().isBlank()) {
            sb.append(typstEscape(checklist.description())).append("\n\n");
        }

        int columnCount = 1 + columns.size();
        sb.append("#table(\n  columns: (auto");
        for (int i = 0; i < columns.size(); i++) sb.append(", 1fr");
        sb.append("),\n  align: (left");
        for (int i = 0; i < columns.size(); i++) sb.append(", center");
        sb.append("),\n  table.header(\n    [*Member*]");
        for (var column : columns) {
            sb.append(", [*").append(typstEscape(column.label())).append("*]");
        }
        sb.append("\n  ),\n");

        for (var entry : entries) {
            String name = memberNameResolver.resolveLocal(entry.memberId());
            sb.append("  [")
                    .append(typstEscape(name != null ? name : "#" + entry.memberId()))
                    .append("]");
            for (var column : columns) {
                var cell = cells.get(cellKey(entry.id(), column.id()));
                boolean checked = cell != null && cell.checked();
                String glyph = checked ? "☑" : "☐";
                sb.append(", [").append(glyph);
                if (cell != null && cell.note() != null && !cell.note().isBlank()) {
                    sb.append("\\\n#text(size: 7pt)[")
                            .append(typstEscape(cell.note()))
                            .append("]");
                }
                sb.append("]");
            }
            sb.append(",\n");
        }
        sb.append(")\n");
        log.info(
                "Rendered Typst source for checklist {} ({} entries, {} columns, {} table cells)",
                checklist.id(),
                entries.size(),
                columns.size(),
                entries.size() * columnCount);
        return sb.toString();
    }

    private String latestUpdateForEntry(Map<String, ChecklistCell> cells, int entryId) {
        return cells.values().stream()
                .filter(c -> c.entryId() == entryId)
                .map(ChecklistCell::updatedAt)
                .max(java.util.Comparator.naturalOrder())
                .map(ts -> DATE_TIME_FMT.format(ts.atZone(java.time.ZoneOffset.UTC)))
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

    private static String typstEscape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("#", "\\#")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }
}
