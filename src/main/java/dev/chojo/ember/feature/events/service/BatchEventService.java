/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Singleton
public class BatchEventService {
    private final EventService eventService;
    private final EventFieldService eventFieldService;
    private final EventLayoutService eventLayoutService;
    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;

    @Inject
    public BatchEventService(
            EventService eventService,
            EventFieldService eventFieldService,
            EventLayoutService eventLayoutService,
            EventRepository eventRepository,
            DomainEventBus eventBus) {
        this.eventService = eventService;
        this.eventFieldService = eventFieldService;
        this.eventLayoutService = eventLayoutService;
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
    }

    public List<StationEvent> createBatch(int stationId, BatchRequest request) {
        var fieldDefs = resolveFieldDefs(request);
        var created = new ArrayList<StationEvent>();

        for (var row : request.rows()) {
            Instant startTime = row.startTime();
            Instant endTime = row.endTime();
            String eventName = row.name() != null ? row.name() : request.name();

            // Use createWithoutEvent to suppress per-row EventCreated fan-out — we emit one
            // aggregate EventsBatchCreated below so users get a single notification.
            var event = eventService.createWithoutEvent(
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

            if (request.restrictedUserTypes() != null
                    || request.restrictedGroupIds() != null
                    || request.restrictedTagIds() != null) {
                eventService.setRestrictions(
                        event.id(),
                        request.restrictedUserTypes() != null ? request.restrictedUserTypes() : List.of(),
                        request.restrictedGroupIds() != null ? request.restrictedGroupIds() : List.of(),
                        request.restrictedTagIds() != null ? request.restrictedTagIds() : List.of(),
                        List.of());
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
        }
        return created;
    }

    public List<BatchRow> generateDates(int stationId, IntervalConfig interval, boolean ignoreBreaks) {
        var dates = expandInterval(interval);
        if (!ignoreBreaks) {
            dates = dates.stream()
                    .filter(date -> !eventRepository.isDateInBreak(stationId, date))
                    .toList();
        }
        LocalTime startOfDay = interval.startTime() != null ? interval.startTime() : LocalTime.of(0, 0);
        LocalTime endOfDay = interval.endTime() != null ? interval.endTime() : LocalTime.of(23, 59);

        return dates.stream()
                .map(date -> new BatchRow(
                        null,
                        date.atTime(startOfDay).toInstant(ZoneOffset.UTC),
                        date.atTime(endOfDay).toInstant(ZoneOffset.UTC),
                        Map.of()))
                .toList();
    }

    private List<EventLayoutField> resolveFieldDefs(BatchRequest request) {
        if (request.layoutId() != null) {
            return eventLayoutService.findFieldsByLayout(request.layoutId());
        }
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
                    for (int d = 1; d <= 7 && !cursor.plusDays(d - 1).isAfter(end); d++) {
                        LocalDate candidate = cursor.plusDays(d - 1);
                        if (candidate.getDayOfWeek().getValue() == dayOfWeek && !candidate.isBefore(start)) {
                            dates.add(candidate);
                            break;
                        }
                    }
                    cursor = cursor.plusMonths(1).withDayOfMonth(1);
                }
                yield dates;
            }
            case QUARTERLY -> {
                LocalDate cursor = start.withDayOfMonth(1);
                while (!cursor.isAfter(end)) {
                    if ((cursor.getMonthValue() - 1) % 3 == 0) {
                        for (int d = 1; d <= 7 && !cursor.plusDays(d - 1).isAfter(end); d++) {
                            LocalDate candidate = cursor.plusDays(d - 1);
                            if (candidate.getDayOfWeek().getValue() == dayOfWeek && !candidate.isBefore(start)) {
                                dates.add(candidate);
                                break;
                            }
                        }
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
            default -> dates;
        };
    }
}
