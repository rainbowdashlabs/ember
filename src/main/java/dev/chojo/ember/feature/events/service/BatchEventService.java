/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventsBatchCreated;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class BatchEventService {
    private static final Logger log = LoggerFactory.getLogger(BatchEventService.class);

    private final EventCrudService crudService;
    private final EventRestrictionService restrictionService;
    private final EventFieldService eventFieldService;
    private final EventBreakRepository breakRepository;
    private final StationRepository stationRepository;
    private final DomainEventBus eventBus;

    @Inject
    public BatchEventService(
            EventCrudService crudService,
            EventRestrictionService restrictionService,
            EventFieldService eventFieldService,
            EventBreakRepository breakRepository,
            StationRepository stationRepository,
            DomainEventBus eventBus) {
        this.crudService = crudService;
        this.restrictionService = restrictionService;
        this.eventFieldService = eventFieldService;
        this.breakRepository = breakRepository;
        this.stationRepository = stationRepository;
        this.eventBus = eventBus;
    }

    /**
     * Creates one event per row. Each is created without its own announcement, and one aggregate
     * announcement covers the whole batch, so members get a single notification.
     *
     * @param stationId the station the events belong to
     * @param request   the rows and what they share
     * @return the events created
     */
    public List<StationEvent> createBatch(int stationId, BatchRequest request) {
        request.rows().forEach(row -> nameOf(row, request));
        var fieldDefs = resolveFieldDefs(request);
        var created = new ArrayList<StationEvent>();

        for (var row : request.rows()) {
            Instant startTime = row.startTime();
            Instant endTime = row.endTime();
            String eventName = nameOf(row, request);

            var event = crudService.createWithoutEvent(
                    stationId,
                    eventName,
                    request.description(),
                    StationEvent.EventType.ONE_TIME,
                    null,
                    startTime,
                    endTime,
                    request.templateId(),
                    Boolean.TRUE.equals(request.requiresRegistration()),
                    request.registrationDeadline(),
                    Boolean.TRUE.equals(request.requiresConfirmation()),
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
                    .map(def -> new EventFieldDraft(
                            def.name(),
                            def.fieldType(),
                            def.config(),
                            row.fieldValues().getOrDefault(def.name(), ""),
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

    /**
     * The dates of a batch, each with the times of day the interval names placed on it.
     *
     * <p>The times are read on the clock of whoever typed them: a drill entered as seven in the
     * evening was stored as seven in the evening UTC, and every appointment of the batch then stood
     * two hours early in Berlin in summer. The zone is the one the browser sent, or the station's
     * own where none came, and each date is converted on its own so a series across a clock change
     * keeps its time of day.
     *
     * @param stationId    the station whose breaks are skipped
     * @param interval     the interval and the times of day
     * @param ignoreBreaks whether dates in a break are kept
     * @param timeZone     the zone the times of day are meant in, or {@code null} for the station's
     * @return one row per date, without a name or values
     */
    public List<BatchRow> generateDates(
            int stationId, IntervalConfig interval, boolean ignoreBreaks, @Nullable String timeZone) {
        var dates = expandInterval(interval);
        if (!ignoreBreaks) {
            dates = dates.stream()
                    .filter(date -> !breakRepository.isDateInBreak(stationId, date))
                    .toList();
        }
        LocalTime startOfDay = interval.startTime() != null ? interval.startTime() : LocalTime.of(0, 0);
        LocalTime endOfDay = interval.endTime() != null ? interval.endTime() : LocalTime.of(23, 59);
        ZoneId zone = zoneOf(stationId, timeZone);

        var rows = dates.stream()
                .map(date -> new BatchRow(
                        null,
                        date.atTime(startOfDay).atZone(zone).toInstant(),
                        date.atTime(endOfDay).atZone(zone).toInstant(),
                        Map.of()))
                .toList();
        log.info("Generated {} batch date rows for station {}", rows.size(), stationId);
        return rows;
    }

    /**
     * The zone a batch's times of day are read in: the one asked for where it parses, otherwise the
     * station's own, which is UTC for a station that keeps none.
     */
    private ZoneId zoneOf(int stationId, @Nullable String timeZone) {
        if (timeZone != null && !timeZone.isBlank()) {
            try {
                return ZoneId.of(timeZone);
            } catch (DateTimeException e) {
                log.warn("Batch dates for station {} asked for the unusable zone '{}'", stationId, timeZone, e);
            }
        }
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * The name a row's appointment is given: its own, or the batch's where it has none. Every row is
     * asked before the first appointment is written, so a nameless row refuses the whole batch
     * rather than leaving half of it behind.
     */
    private static String nameOf(BatchRow row, BatchRequest request) {
        String name = Objects.requireNonNullElse(row.name(), Objects.requireNonNullElse(request.name(), ""));
        if (name.isBlank()) throw EventRefusal.EVENT_NEEDS_A_NAME.raise();
        return name;
    }

    /**
     * The fields every appointment of the batch asks.
     *
     * @throws io.javalin.http.HttpResponseException for a type an appointment does not offer
     */
    private List<BatchFieldEntry> resolveFieldDefs(BatchRequest request) {
        var fields = Objects.requireNonNullElse(request.inlineFields(), List.<BatchFieldEntry>of());
        if (fields.stream().anyMatch(field -> !FieldTypes.APPOINTMENT.contains(field.fieldType()))) {
            throw EventRefusal.BATCH_FIELD_TYPE_NOT_OFFERED.raise();
        }
        return fields;
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
