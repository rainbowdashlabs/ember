/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventCategoryRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.QuestionText;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.station.repository.StationRepository.StationLogo;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final String[] DAY_NAMES_EN = {"", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};

    private final EventRepository eventRepository;
    private final EventCategoryRepository categoryRepository;
    private final OccurrenceCalendar occurrenceCalendar;
    private final EventFieldRepository eventFieldRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository memberRepository;
    private final Api apiConfig;

    @Inject
    public EventExportService(
            EventRepository eventRepository,
            EventCategoryRepository categoryRepository,
            OccurrenceCalendar occurrenceCalendar,
            EventFieldRepository eventFieldRepository,
            StationRepository stationRepository,
            StationMemberRepository memberRepository,
            Api apiConfig) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
        this.occurrenceCalendar = occurrenceCalendar;
        this.eventFieldRepository = eventFieldRepository;
        this.stationRepository = stationRepository;
        this.memberRepository = memberRepository;
        this.apiConfig = apiConfig;
    }

    public Optional<ExportedDocument> exportPdf(
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
        var calendar = occurrenceCalendar.forStation(stationId);

        var columnHeaders = columns.stream().map(ExportColumn::label).toList();

        var expandedEvents = expandEvents(allEvents, from, to, calendar);

        String language = StationFormat.languageOf(station);
        var catGroups = new ArrayList<CategoryGroup>();

        for (var cat : eventCategories) {
            if (!categoryIds.isEmpty() && !categoryIds.contains(cat.id())) continue;
            var catEvents = expandedEvents.stream()
                    .filter(e ->
                            cat.id() == Objects.requireNonNullElse(e.event().categoryId(), -1))
                    .toList();
            if (catEvents.isEmpty()) continue;
            catGroups.add(new CategoryGroup(cat.name(), buildEventRows(catEvents, columns, zone, language)));
        }

        if (categoryIds.isEmpty() || categoryIds.contains(-1)) {
            var uncategorized = expandedEvents.stream()
                    .filter(e -> e.event().categoryId() == null)
                    .toList();
            if (!uncategorized.isEmpty()) {
                catGroups.add(new CategoryGroup("", buildEventRows(uncategorized, columns, zone, language)));
            }
        }

        var data = new LinkedHashMap<String, Object>();
        data.put("stationName", station != null ? station.name() : "");
        data.put("generatedBy", generatedBy != null ? generatedBy : "");
        data.put("generatedAt", DATE_TIME_FMT.format(Instant.now().atZone(zone)));
        data.put("baseUrl", apiConfig.baseUrl());
        data.put("showInstanceUrl", StationFormat.showsInstanceUrl(station));
        data.put("hasLogo", false);
        data.put("dateRange", DATE_FMT.format(from) + " – " + DATE_FMT.format(to));
        data.put("columns", columnHeaders);
        data.put("categories", catGroups);

        try {
            var logo = stationRepository.findLogo(stationId);
            String locale = StationFormat.languageOf(station);
            String filename = DocumentName.of("pdf", DocumentWord.EVENTS.in(locale), spanLabel(from, to, zone, locale));
            return Optional.of(
                    new ExportedDocument(renderPdf(data, locale + "/event-list.typ", logo.orElse(null)), filename));
        } catch (Exception e) {
            log.error("Failed to export event list PDF for station {}", stationId, e);
            return Optional.empty();
        }
    }

    /** Every date inside the period that one of these appointments takes place on, earliest first. */
    private static List<ExpandedEvent> expandEvents(
            List<StationEvent> events, LocalDate from, LocalDate to, StationCalendar calendar) {
        var result = new ArrayList<ExpandedEvent>();
        for (var event : events) {
            for (var date : calendar.between(event, from, to)) result.add(new ExpandedEvent(event, date));
        }
        result.sort(Comparator.comparing(ExpandedEvent::date));
        return result;
    }

    private List<EventRow> buildEventRows(
            List<ExpandedEvent> events, List<ExportColumn> columns, ZoneId zone, String language) {
        boolean needsFields = columns.stream().anyMatch(c -> "field".equals(c.type()));
        var rows = new ArrayList<EventRow>();
        for (var expanded : events) {
            var event = expanded.event();
            Map<String, String> fieldMap = Map.of();
            if (needsFields) {
                var fields = eventFieldRepository.findByEventOn(event.id(), expanded.date());
                fieldMap = fieldCells(fields, memberNames(fields), language);
            }
            var values = new ArrayList<String>();
            for (var col : columns) {
                if ("field".equals(col.type())) {
                    values.add(fieldMap.getOrDefault(col.fieldName(), ""));
                } else {
                    values.add(resolveBuiltinValue(event, expanded.date(), col.key(), zone, language));
                }
            }
            rows.add(new EventRow(values));
        }
        return rows;
    }

    /**
     * An appointment's fields as the sheet prints them, by field name.
     *
     * <p>Each value is written the way every export writes one: members by name, a yes as a word in
     * the station's language, a date as a day.
     *
     * @param fields   the appointment's fields on the day printed
     * @param names    the names of the members the fields name, by member id
     * @param language the station's language
     * @return the printed value of each field, by its name
     */
    static Map<String, String> fieldCells(List<AppointmentField> fields, Map<Integer, String> names, String language) {
        var cells = new LinkedHashMap<String, String>();
        for (var field : fields) {
            cells.put(field.name(), QuestionText.format(field.fieldType(), field.value(), names, language));
        }
        return cells;
    }

    private Map<Integer, String> memberNames(List<AppointmentField> fields) {
        var ids = fields.stream()
                .filter(field -> field.fieldType().namesMembers())
                .flatMap(field -> QuestionValues.memberIds(QuestionValues.read(field.value())).stream())
                .distinct()
                .toList();
        return ids.isEmpty() ? Map.of() : memberRepository.findDisplayNames(ids);
    }

    /**
     * One cell of the sheet, written in the station's own language.
     *
     * <p>The language reaches here because the words are values rather than chrome: a station reading
     * English got an English heading over a column saying Wöchentlich, which is worse than either
     * language on its own.
     */
    private String resolveBuiltinValue(StationEvent event, LocalDate date, String key, ZoneId zone, String language) {
        if (key == null) return "";
        boolean english = "en".equals(language);
        return switch (key) {
            case "name" -> event.name() != null ? event.name() : "";
            case "type" ->
                switch (event.eventType()) {
                    case RECURRING -> english ? "Weekly" : "Wöchentlich";
                    case MONTHLY_FIRST -> english ? "Monthly" : "Monatlich";
                    case QUARTERLY -> english ? "Quarterly" : "Vierteljährlich";
                    case YEARLY -> english ? "Yearly" : "Jährlich";
                    case ONE_TIME -> english ? "Once" : "Einmalig";
                };
            case "day" -> {
                Integer dayOfWeek = event.dayOfWeek();
                yield dayOfWeek != null ? dayName(dayOfWeek, english) : "";
            }
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
            case "description" -> Objects.requireNonNullElse(event.description(), "");
            default -> "";
        };
    }

    private static String dayName(int dayOfWeek, boolean english) {
        var names = english ? DAY_NAMES_EN : DAY_NAMES;
        return dayOfWeek >= 0 && dayOfWeek < names.length ? names[dayOfWeek] : "";
    }

    /**
     * How the chosen days are said in the name.
     *
     * <p>A whole month or a whole year is called what a reader calls it. Anything else is the two days
     * themselves, which is the only honest thing to say about a span that is not a period.
     *
     * <p>The last day is accepted both as the last day covered and as the first day after, because a
     * range is written both ways and a name must not depend on which.
     */
    static String spanLabel(LocalDate from, LocalDate to, ZoneId zone, String locale) {
        Instant start = from.atStartOfDay(zone).toInstant();
        if (covers(from, to, from.plusMonths(1)) && from.getDayOfMonth() == 1) {
            return DocumentPeriod.of("month", start, zone, locale);
        }
        if (covers(from, to, from.plusYears(1)) && from.getDayOfYear() == 1) {
            return DocumentPeriod.of("year", start, zone, locale);
        }
        return DocumentPeriod.day(start, zone) + " - "
                + DocumentPeriod.day(to.atStartOfDay(zone).toInstant(), zone);
    }

    private static boolean covers(LocalDate from, LocalDate to, LocalDate exclusiveEnd) {
        return !from.isAfter(to) && (to.equals(exclusiveEnd) || to.equals(exclusiveEnd.minusDays(1)));
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
