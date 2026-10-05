/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.inventory.entity.Inventory;
import dev.chojo.ember.feature.inventory.entity.ItemMovement;
import dev.chojo.ember.feature.inventory.entity.MovementPurpose;
import dev.chojo.ember.feature.inventory.repository.InventoryRepository;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.DocumentName;
import dev.chojo.ember.util.DocumentPeriod;
import dev.chojo.ember.util.DocumentWord;
import dev.chojo.ember.util.ExportedDocument;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.text.Collator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * The sheet a station takes to the shelf: one row per member, one column per inventory, and the size
 * each of their movements is about.
 *
 * <p>It reads movements rather than any status of theirs. What the sheet is for is the sizes, and a row
 * about a member who is not part of the movement has nothing to write, so movements with no member at
 * either end are left out.
 */
@Singleton
public class MovementExportService {
    private static final Logger log = getLogger(MovementExportService.class);
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * How two names are put in order for the sheet.
     *
     * <p>A plain string comparison orders by code point, which puts every name starting with an
     * umlaut after Z. On a list of German names that is not a sorted list at all, so the comparison
     * follows the language's own alphabet instead.
     */
    private static final Collator BY_NAME = germanCollator();

    private static Collator germanCollator() {
        Collator collator = Collator.getInstance(Locale.GERMAN);
        collator.setStrength(Collator.SECONDARY);
        return collator;
    }

    /**
     * What stands in a size column for a piece that comes in one size only.
     *
     * <p>The column is as wide as a size, and a size is two or three characters. Writing out that the
     * piece has none took more room than the column has and pushed the sheet out of shape, for a cell
     * whose only job is to say that there is nothing to choose. A mark does that in one character and
     * in every language.
     */
    private static final String UNSIZED = "x";

    private final ItemMovementService movementService;
    private final InventoryRepository inventoryRepository;
    private final StationMemberRepository stationMemberRepository;
    private final AccountRepository accountRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldRepository profileFieldRepository;
    private final StationLogoService logoService;
    private final Api apiConfig;

    @Inject
    public MovementExportService(
            ItemMovementService movementService,
            InventoryRepository inventoryRepository,
            StationMemberRepository stationMemberRepository,
            AccountRepository accountRepository,
            StationRepository stationRepository,
            ProfileFieldRepository profileFieldRepository,
            StationLogoService logoService,
            Api apiConfig) {
        this.movementService = movementService;
        this.inventoryRepository = inventoryRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.accountRepository = accountRepository;
        this.stationRepository = stationRepository;
        this.profileFieldRepository = profileFieldRepository;
        this.logoService = logoService;
        this.apiConfig = apiConfig;
    }

    /**
     * Exports the selected movements as a PDF. Groups them by member, resolves size labels, and renders
     * the Typst template. A row is a member and a column an inventory, so a movement missing either is
     * left off the sheet.
     *
     * @param stationId     the station ID
     * @param movementIds   the movements to include, or empty for all of the station's
     * @param extraFieldIds additional profile field IDs to include as columns
     * @param generatedBy   the name of the person generating the export
     * @return the PDF bytes, or empty if no data or rendering failed
     */
    public Optional<ExportedDocument> exportPdf(
            int stationId, List<Integer> movementIds, List<Integer> extraFieldIds, String generatedBy) {
        var station = stationRepository.findById(stationId).orElse(null);
        if (station == null) return Optional.empty();

        var allExchanges = movementService.findByStation(stationId).stream()
                .filter(movement -> movement.memberId() != null && movement.inventoryId() != null)
                .toList();
        var selectedExchanges = movementIds.isEmpty()
                ? allExchanges
                : allExchanges.stream()
                        .filter(e -> movementIds.contains(e.id()))
                        .toList();
        if (selectedExchanges.isEmpty()) return Optional.empty();

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

        var profileColumns = ProfileColumns.of(profileFieldRepository, extraFieldIds, locale);
        var extraFieldNames = profileColumns.names();

        var exchangesByMember = new LinkedHashMap<Integer, List<ItemMovement>>();
        for (var ex : selectedExchanges) {
            exchangesByMember
                    .computeIfAbsent(ex.memberId(), _ -> new ArrayList<>())
                    .add(ex);
        }
        var columnsPerInventory = columnsPerInventory(inventoryOrder, exchangesByMember.values());

        var rows = new ArrayList<Map<String, Object>>();
        for (var entry : exchangesByMember.entrySet()) {
            int memberId = entry.getKey();
            var memberExchanges = entry.getValue();

            var member = stationMemberRepository.findById(memberId).orElse(null);
            Integer accountId = member != null ? member.accountId() : null;
            var account =
                    accountId != null ? accountRepository.findById(accountId).orElse(null) : null;
            String firstName = account != null ? account.firstName() : "";
            String lastName = account != null ? account.lastName() : "";

            var extraFieldValues = extraFieldNames.isEmpty()
                    ? List.<String>of()
                    : profileColumns.cellsOf(profileFieldRepository.findValues(memberId));

            var exchanges = new ArrayList<SizeChange>();
            for (int invId : inventoryOrder) {
                var sizeMap = inventorySizes.get(invId);
                var matching = memberExchanges.stream()
                        .filter(e -> e.inventoryId() == invId)
                        .toList();
                for (var ex : matching) {
                    String oldSize = ex.oldSizeId() != null ? sizeMap.getOrDefault(ex.oldSizeId(), "?") : UNSIZED;
                    String newSize = ex.newSizeId() != null ? sizeMap.getOrDefault(ex.newSizeId(), "?") : UNSIZED;
                    exchanges.add(new SizeChange(oldSize, newSize));
                }
                for (int empty = matching.size(); empty < columnsPerInventory.get(invId); empty++) {
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

        rows.sort(Comparator.<Map<String, Object>, String>comparing(r -> (String) r.get("lastName"), BY_NAME)
                .thenComparing(r -> (String) r.get("firstName"), BY_NAME));

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
        data.put(
                "inventoryColumns",
                inventoryOrder.stream()
                        .flatMap(invId ->
                                Collections.nCopies(columnsPerInventory.get(invId), inventoryNames.get(invId)).stream())
                        .toList());
        data.put("rows", rows);

        var logo = logoService.original(stationId).orElse(null);
        try {
            String filename = exportFileName(selectedExchanges, StationFormat.timezoneOf(station), locale);
            return Optional.of(new ExportedDocument(renderPdf(data, locale + "/exchange-export.typ", logo), filename));
        } catch (Exception e) {
            log.error("Failed to export exchange PDF", e);
            return Optional.empty();
        }
    }

    /**
     * How many columns each inventory takes: as many as the most movements of it any one member has, so
     * someone exchanging two shirts gets two shirt columns instead of losing one of them.
     *
     * @param inventoryOrder    the inventories on the sheet
     * @param movementsByMember each member's movements
     * @return the column count per inventory, at least one each
     */
    static Map<Integer, Integer> columnsPerInventory(
            Set<Integer> inventoryOrder, Collection<List<ItemMovement>> movementsByMember) {
        var columns = new LinkedHashMap<Integer, Integer>();
        for (int invId : inventoryOrder) columns.put(invId, 1);
        for (var movements : movementsByMember) {
            var counts =
                    movements.stream().collect(Collectors.groupingBy(ItemMovement::inventoryId, Collectors.counting()));
            counts.forEach((invId, count) -> columns.merge(invId, count.intValue(), Math::max));
        }
        return columns;
    }

    /**
     * What a queue export is called, which is whatever is in it.
     *
     * <p>A sheet of nothing but exchanges is an exchange sheet, and one holding exchanges and issues
     * says both. The purposes are named in the order they are declared rather than the order the rows
     * were picked, so the same selection always produces the same name.
     *
     * <p>All four purposes at once makes a name nobody reads, so that case is the word for the whole
     * thing instead.
     */
    static String exportFileName(List<ItemMovement> movements, ZoneId zone, String locale) {
        var present = movements.stream().map(ItemMovement::purpose).collect(Collectors.toSet());
        var named = Arrays.stream(MovementPurpose.values())
                .filter(present::contains)
                .map(purpose -> wordFor(purpose).in(locale))
                .toList();
        String what = named.size() == MovementPurpose.values().length || named.isEmpty()
                ? DocumentWord.MOVEMENTS.in(locale)
                : String.join(" - ", named);
        return DocumentName.of("pdf", what, DocumentPeriod.day(Instant.now(), zone));
    }

    private static DocumentWord wordFor(MovementPurpose purpose) {
        return switch (purpose) {
            case ISSUE -> DocumentWord.ISSUE;
            case RETURN -> DocumentWord.RETURN;
            case EXCHANGE -> DocumentWord.EXCHANGE;
            case REQUEST -> DocumentWord.REQUEST;
        };
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, MediaContent logo)
            throws IOException, InterruptedException {
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    record SizeChange(String oldSize, String newSize) {}
}
