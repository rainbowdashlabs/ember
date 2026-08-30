/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class BatchEventService {
    private static final Logger log = LoggerFactory.getLogger(BatchEventService.class);

    private final EventCrudService crudService;
    private final EventRestrictionService restrictionService;
    private final EventFieldService eventFieldService;
    private final EventBreakRepository breakRepository;
    private final DomainEventBus eventBus;

    @Inject
    public BatchEventService(
            EventCrudService crudService,
            EventRestrictionService restrictionService,
            EventFieldService eventFieldService,
            EventBreakRepository breakRepository,
            DomainEventBus eventBus) {
        this.crudService = crudService;
        this.restrictionService = restrictionService;
        this.eventFieldService = eventFieldService;
        this.breakRepository = breakRepository;
        this.eventBus = eventBus;
    }

    public List<StationEvent> createBatch(int stationId, BatchRequest request) {
        var fieldDefs = resolveFieldDefs(request);
        var created = new ArrayList<StationEvent>();

        for (var row : request.rows()) {
            Instant startTime = row.startTime();
            Instant endTime = row.endTime();
            String eventName = row.name() != null ? row.name() : request.name();

            // Use createWithoutEvent to suppress per-row EventCreated fan-out - we emit one
            // aggregate EventsBatchCreated below so users get a single notification.
            var event = crudService.createWithoutEvent(
                    stationId,
                    eventName,
                    request.description(),
                    StationEvent.EventType.ONE_TIME,
                    null,
                    startTime,
                    endTime,
                    request.templateId(),
                    request.requiresRegistration() != null && request.requiresRegistration(),
                    request.registrationDeadline(),
                    request.requiresConfirmation() != null && request.requiresConfirmation(),
                    request.categoryId(),
                    null,
                    null,
                    null,
                    null);

            if (request.restriction() != null) {
                restrictionService.setRestrictions(event.id(), request.restriction());
            }
            if (request.viewRestriction() != null) {
                restrictionService.setViewRestrictions(event.id(), request.viewRestriction());
            }

            var fieldEntries = fieldDefs.stream()
                    .map(def -> new EventFieldRepository.FieldEntry(
                            def.name(),
                            def.fieldType(),
                            def.config(),
                            row.fieldValues() != null ? row.fieldValues().getOrDefault(def.name(), "") : "",
                            def.overview(),
                            def.attendanceFieldId(),
                            false))
                    .toList();
            eventFieldService.replaceFields(event.id(), fieldEntries);
            created.add(event);
        }
        if (!created.isEmpty()) {
            eventBus.publish(new EventsBatchCreated(stationId, List.copyOf(created)));
            log.info("Created batch of {} events for station {}", created.size(), stationId);
        } else {
            log.warn("Batch event creation for station {} produced no events", stationId);
        }
        return created;
    }

    public List<BatchRow> generateDates(int stationId, IntervalConfig interval, boolean ignoreBreaks) {
        var dates = expandInterval(interval);
        if (!ignoreBreaks) {
            dates = dates.stream()
                    .filter(date -> !breakRepository.isDateInBreak(stationId, date))
                    .toList();
        }
        LocalTime startOfDay = interval.startTime() != null ? interval.startTime() : LocalTime.of(0, 0);
        LocalTime endOfDay = interval.endTime() != null ? interval.endTime() : LocalTime.of(23, 59);

        var rows = dates.stream()
                .map(date -> new BatchRow(
                        null,
                        date.atTime(startOfDay).toInstant(ZoneOffset.UTC),
                        date.atTime(endOfDay).toInstant(ZoneOffset.UTC),
                        Map.of()))
                .toList();
        log.info("Generated {} batch date rows for station {}", rows.size(), stationId);
        return rows;
    }

    private List<BatchFieldEntry> resolveFieldDefs(BatchRequest request) {
        return request.inlineFields() != null ? request.inlineFields() : List.of();
    }

    private List<LocalDate> expandInterval(IntervalConfig interval) {
        var dates = new ArrayList<LocalDate>();
        if (interval.intervalType() == null) return dates;
        LocalDate start = interval.startDate();
        LocalDate end = interval.endDate();
        int dayOfWeek = interval.dayOfWeek();

        return switch (interval.intervalType()) {
            case RECURRING -> {
                LocalDate cursor = start;
                while (!cursor.isAfter(end)) {
                    if (cursor.getDayOfWeek().getValue() == dayOfWeek) {
                        dates.add(cursor);
                    }
                    cursor = cursor.plusDays(1);
                }
                yield dates;
            }
            case MONTHLY_FIRST -> {
                LocalDate cursor = start.withDayOfMonth(1);
                while (!cursor.isAfter(end)) {
                    addFirstMatchingWeekday(dates, cursor, start, end, dayOfWeek);
                    cursor = cursor.plusMonths(1).withDayOfMonth(1);
                }
                yield dates;
            }
            case QUARTERLY -> {
                LocalDate cursor = start.withDayOfMonth(1);
                while (!cursor.isAfter(end)) {
                    if ((cursor.getMonthValue() - 1) % 3 == 0) {
                        addFirstMatchingWeekday(dates, cursor, start, end, dayOfWeek);
                    }
                    cursor = cursor.plusMonths(1).withDayOfMonth(1);
                }
                yield dates;
            }
            case YEARLY -> {
                LocalDate cursor = start;
                while (!cursor.isAfter(end)) {
                    dates.add(cursor);
                    cursor = cursor.plusYears(1);
                }
                yield dates;
            }
        };
    }

    /**
     * Adds the first day within the seven-day window opening at {@code cursor} that falls on the
     * target weekday and is not before {@code start}, bounded by {@code end}.
     */
    private void addFirstMatchingWeekday(
            List<LocalDate> dates, LocalDate cursor, LocalDate start, LocalDate end, int dayOfWeek) {
        for (int d = 1; d <= 7 && !cursor.plusDays(d - 1).isAfter(end); d++) {
            LocalDate candidate = cursor.plusDays(d - 1);
            if (candidate.getDayOfWeek().getValue() == dayOfWeek && !candidate.isBefore(start)) {
                dates.add(candidate);
                break;
            }
        }
    }
}
