/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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

@Singleton
public class EventExportService {
    private static final Logger log = getLogger(EventExportService.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String TYPST_BIN = System.getenv().getOrDefault("TYPST_BIN", "typst");

    private static final String[] DAY_NAMES = {"", "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public EventExportService(
            EventRepository eventRepository,
            EventFieldRepository eventFieldRepository,
            StationRepository stationRepository,
            Api apiConfig) {
        this.eventRepository = eventRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    public record ExportColumn(String type, String key, String fieldName, String label) {}

    public Optional<byte[]> exportPdf(
            int stationId,
            List<Integer> categoryIds,
            List<ExportColumn> columns,
            LocalDate from,
            LocalDate to,
            String generatedBy) {
        var station = stationRepository.findById(stationId).orElse(null);
        ZoneId zone;
        if (station != null && station.timezone() != null) {
            ZoneId parsed;
            try {
                parsed = ZoneId.of(station.timezone());
            } catch (Exception ignored) {
                parsed = ZoneOffset.UTC;
            }
            zone = parsed;
        } else {
            zone = ZoneOffset.UTC;
        }

        var allEvents = eventRepository.findByStation(stationId);
        var eventCategories = eventRepository.findCategoriesByStation(stationId);
        var breaks = eventRepository.findBreaksByStation(stationId);

        // Build column headers in order
        var columnHeaders = columns.stream().map(ExportColumn::label).toList();

        // Expand recurring events into individual occurrences
        var expandedEvents = expandEvents(allEvents, from, to, breaks, zone);

        // Group events by category
        var catGroups = new ArrayList<Map<String, Object>>();
        Set<Integer> seenIds = new LinkedHashSet<>();

        for (var cat : eventCategories) {
            if (!categoryIds.isEmpty() && !categoryIds.contains(cat.id())) continue;
            var catEvents = expandedEvents.stream()
                    .filter(e -> cat.id()
                            == (e.event().categoryId() != null ? e.event().categoryId() : -1))
                    .toList();
            if (catEvents.isEmpty()) continue;
            catEvents.forEach(e -> seenIds.add(e.event().id()));
            catGroups.add(Map.of("name", cat.name(), "events", buildEventRows(catEvents, columns, zone)));
        }

        // Uncategorized
        if (categoryIds.isEmpty() || categoryIds.contains(-1)) {
            var uncategorized = expandedEvents.stream()
                    .filter(e -> e.event().categoryId() == null)
                    .toList();
            if (!uncategorized.isEmpty()) {
                catGroups.add(Map.of("name", "", "events", buildEventRows(uncategorized, columns, zone)));
            }
        }

        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("hasLogo", false);
        data.put("dateRange", DATE_FMT.format(from) + " – " + DATE_FMT.format(to));
        data.put("columns", columnHeaders);
        data.put("categories", catGroups);

        try {
            var logo = stationRepository.findLogo(stationId);
            String locale = resolveLocalePrefix(station);
            return Optional.of(renderPdf(data, locale + "/event-list.typ", logo.orElse(null)));
        } catch (Exception e) {
            log.error("Failed to export event list PDF for station {}", stationId, e);
            return Optional.empty();
        }
    }

    private record ExpandedEvent(StationEvent event, LocalDate date) {}

    private List<ExpandedEvent> expandEvents(
            List<StationEvent> events, LocalDate from, LocalDate to, List<EventBreak> breaks, ZoneId zone) {
        var result = new ArrayList<ExpandedEvent>();
        for (var event : events) {
            if (event.isRecurring()) {
                expandRecurring(event, from, to, breaks, result);
            } else {
                if (event.startTime() == null) continue;
                LocalDate eventDate = event.startTime().atZone(zone).toLocalDate();
                if (!eventDate.isBefore(from) && !eventDate.isAfter(to)) {
                    result.add(new ExpandedEvent(event, eventDate));
                }
            }
        }
        result.sort(Comparator.comparing(ExpandedEvent::date));
        return result;
    }

    private void expandRecurring(
            StationEvent event, LocalDate from, LocalDate to, List<EventBreak> breaks, List<ExpandedEvent> result) {
        if (event.dayOfWeek() == null && event.eventType() != StationEvent.EventType.YEARLY) return;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            LocalDate date = d;
            boolean inBreak =
                    breaks.stream().anyMatch(b -> !date.isBefore(b.startDate()) && !date.isAfter(b.endDate()));
            if (inBreak) continue;

            boolean matches =
                    switch (event.eventType()) {
                        case RECURRING -> d.getDayOfWeek().getValue() == event.dayOfWeek();
                        case MONTHLY_FIRST ->
                            d.getDayOfWeek().getValue() == event.dayOfWeek() && d.getDayOfMonth() <= 7;
                        case QUARTERLY ->
                            d.getDayOfWeek().getValue() == event.dayOfWeek()
                                    && d.getDayOfMonth() <= 7
                                    && (d.getMonthValue() - 1) % 3 == 0;
                        case YEARLY ->
                            event.startTime() != null
                                    && d.getMonthValue()
                                            == event.startTime()
                                                    .atZone(ZoneOffset.UTC)
                                                    .getMonthValue()
                                    && d.getDayOfMonth()
                                            == event.startTime()
                                                    .atZone(ZoneOffset.UTC)
                                                    .getDayOfMonth();
                        default -> false;
                    };
            if (matches) result.add(new ExpandedEvent(event, d));
        }
    }

    private List<Map<String, Object>> buildEventRows(
            List<ExpandedEvent> events, List<ExportColumn> columns, ZoneId zone) {
        boolean needsFields = columns.stream().anyMatch(c -> "field".equals(c.type()));
        var rows = new ArrayList<Map<String, Object>>();
        for (var expanded : events) {
            var event = expanded.event();
            Map<String, String> fieldMap = Map.of();
            if (needsFields) {
                var fields = eventFieldRepository.findByEvent(event.id());
                var map = new LinkedHashMap<String, String>();
                for (var f : fields) {
                    map.put(f.name(), f.value());
                }
                fieldMap = map;
            }
            var values = new ArrayList<String>();
            for (var col : columns) {
                if ("field".equals(col.type())) {
                    values.add(fieldMap.getOrDefault(col.fieldName(), ""));
                } else {
                    values.add(resolveBuiltinValue(event, expanded.date(), col.key(), zone));
                }
            }
            rows.add(Map.of("values", values));
        }
        return rows;
    }

    private String resolveBuiltinValue(StationEvent event, LocalDate date, String key, ZoneId zone) {
        if (key == null) return "";
        return switch (key) {
            case "name" -> event.name() != null ? event.name() : "";
            case "type" ->
                switch (event.eventType()) {
                    case RECURRING -> "Wöchentlich";
                    case MONTHLY_FIRST -> "Monatlich";
                    case QUARTERLY -> "Vierteljährlich";
                    case YEARLY -> "Jährlich";
                    case ONE_TIME -> "Einmalig";
                };
            case "day" -> event.dayOfWeek() != null ? DAY_NAMES[event.dayOfWeek()] : "";
            case "date" -> DATE_FMT.format(date);
            case "time" -> {
                String start = event.startTime() != null
                        ? TIME_FMT.format(event.startTime().atZone(zone))
                        : "";
                String end = event.endTime() != null
                        ? TIME_FMT.format(event.endTime().atZone(zone))
                        : "";
                yield start.isEmpty() ? "" : start + " – " + end;
            }
            case "description" -> event.description() != null ? event.description() : "";
            default -> "";
        };
    }

    private String resolveLocalePrefix(Station station) {
        if (station != null && station.locale() != null && station.locale().startsWith("en")) {
            return "en";
        }
        return "de";
    }

    private byte[] renderPdf(Map<String, Object> data, String templateName, StationLogo logo)
            throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("event-list-export");
        try {
            Path templateSource = Path.of("templates", "typst", templateName);
            Path templateFile = tempDir.resolve(templateName);
            Files.createDirectories(templateFile.getParent());
            Files.copy(templateSource, templateFile);

            Path templateDir = templateFile.getParent();

            if (logo != null) {
                String ext =
                        switch (logo.contentType()) {
                            case "image/jpeg" -> "jpg";
                            case "image/svg+xml" -> "svg";
                            default -> "png";
                        };
                Files.write(templateDir.resolve("logo." + ext), logo.data());
                data.put("hasLogo", true);
                data.put("logoFile", "logo." + ext);
            }

            Path dataFile = templateDir.resolve("data.json");
            Files.writeString(dataFile, MAPPER.writeValueAsString(data));

            Path outputFile = tempDir.resolve("events.pdf");

            var process = new ProcessBuilder(TYPST_BIN, "compile", templateFile.toString(), outputFile.toString())
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();

            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                throw new IOException("typst compile failed (exit " + exitCode + "): " + output);
            }

            return Files.readAllBytes(outputFile);
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }
}
