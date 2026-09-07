/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Service for exporting event data as PDF using Typst templates.
 * Supports configurable columns, calendar views, and registration lists.
 */
@Singleton
public class EventExportService {
    private static final Logger log = getLogger(EventExportService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final String[] DAY_NAMES = {"", "Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"};

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final EventBreakRepository breakRepository;
    private final EventFieldRepository eventFieldRepository;
    private final StationRepository stationRepository;
    private final Api apiConfig;

    @Inject
    public EventExportService(
            EventRepository eventRepository,
            EventCategoryRepository categoryRepository,
            EventBreakRepository breakRepository,
            EventFieldRepository eventFieldRepository,
            StationRepository stationRepository,
            Api apiConfig) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.breakRepository = breakRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.stationRepository = stationRepository;
        this.apiConfig = apiConfig;
    }

    public Optional<byte[]> exportPdf(
            int stationId,
            List<Integer> categoryIds,
            List<ExportColumn> columns,
            LocalDate from,
            LocalDate to,
            String generatedBy) {
        var station = stationRepository.findById(stationId).orElse(null);
        ZoneId zone = StationFormat.timezoneOf(station);

        var allEvents = eventRepository.findByStation(stationId);
        var eventCategories = categoryRepository.findByStation(stationId);
        var breaks = breakRepository.findByStation(stationId);

        // Build column headers in order
        var columnHeaders = columns.stream().map(ExportColumn::label).toList();

        // Expand recurring events into individual occurrences
        var expandedEvents = expandEvents(allEvents, from, to, breaks, zone);

        // Group events by category
        var catGroups = new ArrayList<CategoryGroup>();

        for (var cat : eventCategories) {
            if (!categoryIds.isEmpty() && !categoryIds.contains(cat.id())) continue;
            var catEvents = expandedEvents.stream()
                    .filter(e -> cat.id()
                            == (e.event().categoryId() != null ? e.event().categoryId() : -1))
                    .toList();
            if (catEvents.isEmpty()) continue;
            catGroups.add(new CategoryGroup(cat.name(), buildEventRows(catEvents, columns, zone)));
        }

        // Uncategorized
        if (categoryIds.isEmpty() || categoryIds.contains(-1)) {
            var uncategorized = expandedEvents.stream()
                    .filter(e -> e.event().categoryId() == null)
                    .toList();
            if (!uncategorized.isEmpty()) {
                catGroups.add(new CategoryGroup("", buildEventRows(uncategorized, columns, zone)));
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

    private List<ExpandedEvent> expandEvents(
            List<StationEvent> events, LocalDate from, LocalDate to, List<EventBreak> breaks, ZoneId zone) {
        var result = new ArrayList<ExpandedEvent>();
        for (var event : events) {
            if (event.isRecurring()) {
                expandRecurring(event, from, to, breaks, result, zone);
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

    /**
     * Whether a day is the one a yearly appointment repeats on.
     *
     * <p>Which day of the year that is has to be read on the station's own clock, the same clock the
     * rest of this export reads. Read anywhere else, an appointment near midnight belongs to the day
     * either side of the one it is actually on, and the yearly line is then listed on the wrong date.
     *
     * @param startTime when the appointment starts, or null where it has no start
     * @param zone      the station's clock
     * @param day       the day being considered
     * @return true where the day is the appointment's own day of the year
     */
    static boolean fallsOnYearlyAnchor(Instant startTime, ZoneId zone, LocalDate day) {
        if (startTime == null) return false;
        LocalDate anchor = startTime.atZone(zone).toLocalDate();
        return day.getMonthValue() == anchor.getMonthValue() && day.getDayOfMonth() == anchor.getDayOfMonth();
    }

    private void expandRecurring(
            StationEvent event,
            LocalDate from,
            LocalDate to,
            List<EventBreak> breaks,
            List<ExpandedEvent> result,
            ZoneId zone) {
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
                        // Which day of the year an appointment falls on is read in the station's
                        // own timezone, the same way every other date in this export is. Read in
                        // UTC, an appointment late in the evening lands on the day before and
                        // the yearly one is then listed on the wrong date.
                        case YEARLY -> fallsOnYearlyAnchor(event.startTime(), zone, d);
                        default -> false;
                    };
            if (matches && !event.isAfterLastDate(d)) result.add(new ExpandedEvent(event, d));
        }
    }

    private List<EventRow> buildEventRows(List<ExpandedEvent> events, List<ExportColumn> columns, ZoneId zone) {
        boolean needsFields = columns.stream().anyMatch(c -> "field".equals(c.type()));
        var rows = new ArrayList<EventRow>();
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
            rows.add(new EventRow(values));
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
        return TypstCompiler.compileTemplate(
                data,
                templateName,
                logo != null ? new TypstCompiler.StationLogo(logo.data(), logo.contentType()) : null);
    }

    public record ExportColumn(String type, String key, String fieldName, String label) {}

    private record ExpandedEvent(StationEvent event, LocalDate date) {}

    record CategoryGroup(String name, List<EventRow> events) {}

    record EventRow(List<String> values) {}
}
