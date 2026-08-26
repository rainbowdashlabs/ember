/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.ExchangeRequest;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.media.service.ImageVariantService.ImageData;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for exporting exchange requests as PDF documents.
 * Generates Typst-based PDFs with member exchange data, including size changes and profile fields.
 */
@Singleton
public class ExchangeExportService {
    private static final Logger log = getLogger(ExchangeExportService.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private final ExchangeService exchangeService;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final StationLogoService logoService;
    private final Api apiConfig;

    @Inject
    public ExchangeExportService(
            ExchangeService exchangeService,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            ProfileFieldRepository profileFieldRepository,
            StationLogoService logoService,
            Api apiConfig) {
        this.exchangeService = exchangeService;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.logoService = logoService;
        this.apiConfig = apiConfig;
    }

    /**
     * Exports selected exchange requests as a PDF document.
     * Groups exchanges by member, resolves size labels, and renders using a Typst template.
     *
     * @param stationId     the station ID
     * @param exchangeIds   the exchange request IDs to include, or empty for all
     * @param extraFieldIds additional profile field IDs to include as columns
     * @param generatedBy   the name of the person generating the export
     * @return the PDF bytes, or empty if no data or rendering failed
     */
    public Optional<byte[]> exportPdf(
            int stationId, List<Integer> exchangeIds, List<Integer> extraFieldIds, String generatedBy) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return Optional.empty();

        // Load selected exchanges
        var allExchanges = exchangeService.findByStation(stationId);
        var selectedExchanges = exchangeIds.isEmpty()
                ? allExchanges
                : allExchanges.stream()
                        .filter(e -> exchangeIds.contains(e.id()))
                        .toList();
        if (selectedExchanges.isEmpty()) return Optional.empty();

        // Collect inventory names and size maps
        var inventoryNames = new LinkedHashMap<Integer, String>();
        var inventorySizes = new LinkedHashMap<Integer, Map<Integer, String>>();
        Set<Integer> inventoryOrder = new LinkedHashSet<>();
        for (var ex : selectedExchanges) {
            inventoryOrder.add(ex.inventoryId());
        }
        for (int invId : inventoryOrder) {
            var inv = inventoryRepository.findById(invId);
            inventoryNames.put(invId, inv.map(Inventory::name).orElse("#" + invId));
            var sizes = inventoryRepository.findSizes(invId);
            var sizeMap = new LinkedHashMap<Integer, String>();
            for (var s : sizes) sizeMap.put(s.id(), s.label());
            inventorySizes.put(invId, sizeMap);
        }

        String locale = StationFormat.languageOf(station);

        // Resolve extra profile field names
        var extraFieldNames = new ArrayList<String>();
        for (int fieldId : extraFieldIds) {
            profileFieldRepository.findById(fieldId).ifPresent(f -> extraFieldNames.add(f.name()));
        }

        // Group exchanges by member
        var exchangesByMember = new LinkedHashMap<Integer, List<ExchangeRequest>>();
        for (var ex : selectedExchanges) {
            exchangesByMember
                    .computeIfAbsent(ex.memberId(), _ -> new ArrayList<>())
                    .add(ex);
        }

        // Build rows
        var rows = new ArrayList<Map<String, Object>>();
        for (var entry : exchangesByMember.entrySet()) {
            int memberId = entry.getKey();
            var memberExchanges = entry.getValue();

            var member = stationMemberRepository.findById(memberId).orElse(null);
            var account = member != null
                    ? accountRepository.findById(member.accountId()).orElse(null)
                    : null;
            String firstName = account != null ? account.firstName() : "";
            String lastName = account != null ? account.lastName() : "";

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

            // Build exchange columns (one per inventory, with old/new sizes)
            var exchanges = new ArrayList<SizeChange>();
            for (int invId : inventoryOrder) {
                var sizeMap = inventorySizes.get(invId);
                var matching = memberExchanges.stream()
                        .filter(e -> e.inventoryId() == invId)
                        .findFirst();
                if (matching.isPresent()) {
                    var ex = matching.get();
                    String unisize = "de".equals(locale) ? "Einheitsgröße" : "One Size";
                    String oldSize = ex.oldSizeId() != null ? sizeMap.getOrDefault(ex.oldSizeId(), "?") : unisize;
                    String newSize = ex.newSizeId() != null ? sizeMap.getOrDefault(ex.newSizeId(), "?") : unisize;
                    exchanges.add(new SizeChange(oldSize, newSize));
                } else {
                    exchanges.add(new SizeChange("", ""));
                }
            }

            var row = new LinkedHashMap<String, Object>();
            row.put("lastName", lastName);
            row.put("firstName", firstName);
            row.put("extraFieldValues", extraFieldValues);
            row.put("exchanges", exchanges);
            rows.add(row);
        }

        // Sort rows by last name, then first name
        rows.sort(Comparator.<Map<String, Object>, String>comparing(r -> (String) r.get("lastName"))
                .thenComparing(r -> (String) r.get("firstName")));

        // Build data map
        var zone =
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station.name());
        data.put("generatedBy", generatedBy);
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("hasLogo", false);
        data.put("extraFields", extraFieldNames);
        data.put(
                "inventoryColumns",
                inventoryOrder.stream().map(inventoryNames::get).toList());
        data.put("rows", rows);

        // Render
        var logo = logoService.original(stationId).orElse(null);
        try {
            return Optional.of(renderPdf(data, locale + "/exchange-export.typ", logo));
        } catch (Exception e) {
            log.error("Failed to export exchange PDF", e);
            return Optional.empty();
        }
    }

    private String formatFieldValue(String rawValue) {
        if (rawValue == null) return "";
        String val = rawValue.trim();
        if (val.startsWith("\"") && val.endsWith("\"")) {
            val = val.substring(1, val.length() - 1);
        }
        return val;
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, ImageData logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    record SizeChange(String oldSize, String newSize) {}
}
