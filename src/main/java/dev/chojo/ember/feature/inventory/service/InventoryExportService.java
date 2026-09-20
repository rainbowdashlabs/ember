/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.InventoryItem;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.util.CsvWriter;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for exporting inventory member lists as PDF documents.
 * Generates Typst-based PDFs showing which items are assigned to which members.
 */
@Singleton
public class InventoryExportService {
    private static final Logger log = getLogger(InventoryExportService.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final Api apiConfig;

    @Inject
    public InventoryExportService(
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            ProfileFieldRepository profileFieldRepository,
            Api apiConfig) {
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.apiConfig = apiConfig;
    }

    /**
     * Exports an inventory member list as a PDF document.
     * Builds a table of members with their assigned items per inventory, respecting display toggles.
     *
     * @param stationId      the station ID
     * @param memberIds      the member IDs to include
     * @param inventoryIds   the inventory IDs to include, or empty for all
     * @param extraFieldIds  additional profile field IDs to include as columns
     * @param generatedBy    the name of the person generating the export
     * @param showName       whether to show item names
     * @param showInternalId whether to show internal IDs
     * @param showSize       whether to show size labels
     * @return the PDF bytes, or empty if no data or rendering failed
     */
    /**
     * The member list with their gear, printed or as a spreadsheet.
     *
     * @param separator what goes between the cells of a spreadsheet, or {@code null} for the printout
     */
    public Optional<ExportedDocument> export(
            int stationId,
            List<Integer> memberIds,
            List<Integer> inventoryIds,
            List<Integer> extraFieldIds,
            String generatedBy,
            boolean showName,
            boolean showInternalId,
            boolean showSize,
            CsvWriter.Separator separator) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return Optional.empty();

        // Load inventories
        var allInventories = inventoryRepository.findByStation(stationId);
        var selectedInventories = inventoryIds.isEmpty()
                ? allInventories
                : allInventories.stream()
                        .filter(inv -> inventoryIds.contains(inv.id()))
                        .toList();
        if (selectedInventories.isEmpty()) return Optional.empty();

        // Load all items for selected inventories
        var itemsByInventory = new LinkedHashMap<Integer, List<InventoryItem>>();
        for (var inv : selectedInventories) {
            itemsByInventory.put(inv.id(), inventoryRepository.findItems(inv.id()));
        }

        // Build inventory column names
        var inventoryColumns = selectedInventories.stream().map(Inventory::name).toList();

        // Build size maps for each inventory
        var inventorySizes = new LinkedHashMap<Integer, Map<Integer, String>>();
        for (var inv : selectedInventories) {
            var sizes = inventoryRepository.findSizes(inv.id());
            var sizeMap = new LinkedHashMap<Integer, String>();
            for (var s : sizes) sizeMap.put(s.id(), s.label());
            inventorySizes.put(inv.id(), sizeMap);
        }

        String locale = StationFormat.languageOf(station);

        // Resolve extra profile field names
        var extraFieldNames = new ArrayList<String>();
        for (int fieldId : extraFieldIds) {
            profileFieldRepository.findById(fieldId).ifPresent(f -> extraFieldNames.add(f.name()));
        }

        // Build rows for each member
        var rows = new ArrayList<Map<String, Object>>();
        for (int memberId : memberIds) {
            var member = stationMemberRepository.findById(memberId).orElse(null);
            var account = member != null
                    ? accountRepository.findById(member.accountId()).orElse(null)
                    : null;
            String firstName = account != null ? account.firstName() : "";
            String lastName = account != null ? account.lastName() : "";
            String name = account != null ? NameParts.of(account).official() : null;
            if (name == null || name.isEmpty()) name = "#" + memberId;

            // Extra field values
            var extraFieldValues = new ArrayList<String>();
            if (!extraFieldIds.isEmpty()) {
                var values = profileFieldRepository.findValues(memberId);
                for (int fieldId : extraFieldIds) {
                    String val = values.stream()
                            .filter(v -> v.fieldId() == fieldId)
                            .map(v -> formatFieldValue(v.value()))
                            .findFirst()
                            .orElse("");
                    extraFieldValues.add(val);
                }
            }

            // Build items per inventory column (each item: {label, lost})
            var itemColumns = new ArrayList<List<ItemEntry>>();
            for (var inv : selectedInventories) {
                var items = itemsByInventory.getOrDefault(inv.id(), List.of());
                var memberItems = items.stream()
                        .filter(item -> item.assignedTo() != null && item.assignedTo() == memberId)
                        .toList();
                var itemEntries = new ArrayList<ItemEntry>();
                for (var item : memberItems) {
                    var parts = new ArrayList<String>();
                    if (showName && item.name() != null) parts.add(item.name());
                    if (showInternalId
                            && item.internalId() != null
                            && !item.internalId().isEmpty()) {
                        parts.add("(" + item.internalId() + ")");
                    }
                    if (showSize && item.sizeId() != null) {
                        var sizeMap = inventorySizes.get(inv.id());
                        if (sizeMap != null) {
                            String sizeLabel = sizeMap.get(item.sizeId());
                            if (sizeLabel != null) parts.add("[" + sizeLabel + "]");
                        }
                    }
                    String desc = parts.isEmpty() ? (item.name() != null ? item.name() : "–") : String.join(" ", parts);
                    itemEntries.add(new ItemEntry(desc, item.lostAt() != null));
                }
                itemColumns.add(itemEntries);
            }

            var row = new LinkedHashMap<String, Object>();
            row.put("name", name);
            row.put("extraFieldValues", extraFieldValues);
            row.put("items", itemColumns);
            rows.add(row);
        }

        // Sort by name
        rows.sort(Comparator.comparing(r -> (String) r.get("name")));

        // Build data map
        var zone =
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station.name());
        data.put("generatedBy", generatedBy);
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("showInstanceUrl", StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);
        data.put("extraFields", extraFieldNames);
        data.put("inventoryColumns", inventoryColumns);
        data.put("rows", rows);

        String filename = DocumentName.of(
                separator == null ? "pdf" : "csv",
                DocumentWord.MEMBER_INVENTORY.in(locale),
                DocumentPeriod.day(Instant.now(), zone));

        if (separator != null) {
            return Optional.of(ExportedDocument.ofText(
                    asSpreadsheet(inventoryColumns, extraFieldNames, rows, locale, separator), filename));
        }

        StationLogo logo = stationRepository.findLogo(stationId).orElse(null);
        try {
            return Optional.of(new ExportedDocument(renderPdf(data, locale + "/inventory-members.typ", logo), filename));
        } catch (Exception e) {
            log.error("Failed to export inventory members PDF", e);
            return Optional.empty();
        }
    }

    /**
     * The same rows as the sheet, written for a spreadsheet.
     *
     * <p>Built from what the printout is built from rather than assembled a second time, which is
     * what stops the two saying different things about the same member. A member holding several
     * pieces out of one inventory has them in one cell, because a column is an inventory and splitting
     * it would put the same member on two rows.
     */
    private static String asSpreadsheet(
            List<String> inventoryColumns,
            List<String> extraFieldNames,
            List<Map<String, Object>> rows,
            String locale,
            CsvWriter.Separator separator) {
        var headers = new ArrayList<String>();
        headers.add(DocumentWord.MEMBERS.in(locale));
        headers.addAll(extraFieldNames);
        headers.addAll(inventoryColumns);

        var lines = new ArrayList<List<String>>(rows.size());
        for (var row : rows) {
            var cells = new ArrayList<String>(headers.size());
            cells.add(String.valueOf(row.get("name")));
            @SuppressWarnings("unchecked")
            var extras = (List<String>) row.get("extraFieldValues");
            cells.addAll(extras);
            @SuppressWarnings("unchecked")
            var items = (List<List<ItemEntry>>) row.get("items");
            for (var perInventory : items) {
                cells.add(perInventory.stream().map(ItemEntry::label).collect(Collectors.joining(", ")));
            }
            lines.add(List.copyOf(cells));
        }
        return CsvWriter.write(headers, lines, separator);
    }

    private String formatFieldValue(String rawValue) {
        if (rawValue == null) return "";
        String val = rawValue.trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, StationLogo logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    record ItemEntry(String label, boolean lost) {}
}
