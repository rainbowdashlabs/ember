/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventLayoutField;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.LayoutFieldEntry;
import dev.chojo.ember.feature.events.repository.EventLayoutRepository;
import dev.chojo.ember.feature.events.service.BatchEventService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.events.service.EventLayoutService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchEventServiceTest extends RepositoryTestBase {

    private static BatchEventService batchService;
    private static EventService eventService;
    private static EventLayoutService layoutService;
    private static Station station;
    private static int layoutId;

    @BeforeAll
    static void setup() {
        var domainEventBus = new DomainEventBus(Set.of());
        eventService = new EventService(eventRepo, new RestrictionRepository(), domainEventBus);
        var fieldService = new EventFieldService(eventFieldRepo);
        layoutService = new EventLayoutService(new EventLayoutRepository());
        batchService = new BatchEventService(eventService, fieldService, layoutService, eventRepo);

        station = stationRepo.create("BatchEventServiceStation");

        // Create a layout with fields for use in batch tests
        var layout = layoutService.create(station.id(), "Batch Layout");
        layoutId = layout.id();
        layoutService.replaceLayoutFields(
                layoutId,
                List.of(
                        new LayoutFieldEntry(
                                "Location", EventFieldType.STRING, EventFieldConfig.parse("{}"), true, null),
                        new LayoutFieldEntry(
                                "Topic", EventFieldType.STRING, EventFieldConfig.parse("{}"), false, null)));
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createBatchWithInlineFields() {
        Instant start1 = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end1 = start1.plus(2, ChronoUnit.HOURS);
        Instant start2 = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant end2 = start2.plus(2, ChronoUnit.HOURS);

        var inlineFields = List.of(
                new EventLayoutField(
                        0, 0, "Location", EventFieldType.STRING, EventFieldConfig.parse("{}"), 0, true, null),
                new EventLayoutField(
                        0, 0, "Notes", EventFieldType.STRING, EventFieldConfig.parse("{}"), 1, false, null));

        var rows = List.of(
                new BatchRow("Event Day 1", start1, end1, Map.of("Location", "Berlin", "Notes", "Bring gear")),
                new BatchRow("Event Day 2", start2, end2, Map.of("Location", "Munich", "Notes", "Optional")));

        var request = new BatchRequest(
                "Batch Training",
                "Weekly batch",
                null,
                null,
                null,
                inlineFields,
                rows,
                false,
                false,
                null,
                null,
                null,
                null);

        var created = batchService.createBatch(station.id(), request);
        assertEquals(2, created.size());
        assertEquals("Event Day 1", created.get(0).name());
        assertEquals("Event Day 2", created.get(1).name());
        assertTrue(created.stream().allMatch(e -> e.stationId() == station.id()));
    }

    @Test
    @Order(2)
    void createBatchUsesRequestNameWhenRowNameIsNull() {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS);
        Instant end = start.plus(1, ChronoUnit.HOURS);

        var rows = List.of(new BatchRow(null, start, end, Map.of()));

        var request = new BatchRequest(
                "Default Name", "desc", null, null, null, List.of(), rows, false, false, null, null, null, null);

        var created = batchService.createBatch(station.id(), request);
        assertEquals(1, created.size());
        assertEquals("Default Name", created.getFirst().name());
    }

    @Test
    @Order(3)
    void createBatchWithLayout() {
        Instant start = Instant.now().plus(4, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var rows = List.of(new BatchRow("Layout Event", start, end, Map.of("Location", "Hamburg")));

        var request = new BatchRequest(
                "Layout Batch", "desc", null, null, layoutId, null, rows, false, false, null, null, null, null);

        var created = batchService.createBatch(station.id(), request);
        assertEquals(1, created.size());
        assertEquals("Layout Event", created.getFirst().name());
    }

    @Test
    @Order(4)
    void createBatchWithRestrictions() {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);

        var rows = List.of(new BatchRow("Restricted Event", start, end, Map.of()));

        var request = new BatchRequest(
                "Restricted Batch",
                "desc",
                null,
                null,
                null,
                List.of(),
                rows,
                false,
                false,
                null,
                List.of(1),
                List.of(),
                null);

        var created = batchService.createBatch(station.id(), request);
        assertEquals(1, created.size());
        assertEquals("Restricted Event", created.getFirst().name());
    }

    @Test
    @Order(10)
    void generateDatesRecurring() {
        // Monday = 1, generate weekly for 3 weeks
        LocalDate start = LocalDate.of(2026, 6, 1); // Monday
        LocalDate end = LocalDate.of(2026, 6, 22); // covers 4 Mondays: 1, 8, 15, 22
        var config = new IntervalConfig("RECURRING", 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(4, rows.size());
        assertTrue(rows.stream()
                .allMatch(
                        r -> r.startTime().atZone(ZoneOffset.UTC).getDayOfWeek().getValue() == 1));
    }

    @Test
    @Order(11)
    void generateDatesMonthlyFirst() {
        // First Tuesday (dayOfWeek=2) in each month from June to August 2026
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        var config = new IntervalConfig("MONTHLY_FIRST", 2, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(3, rows.size()); // June, July, August
    }

    @Test
    @Order(12)
    void generateDatesQuarterly() {
        // First Wednesday (3) of the first month of each quarter: Jan, Apr, Jul, Oct
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        var config = new IntervalConfig("QUARTERLY", 3, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(4, rows.size()); // Q1, Q2, Q3, Q4
    }

    @Test
    @Order(13)
    void generateDatesYearly() {
        LocalDate start = LocalDate.of(2026, 3, 15);
        LocalDate end = LocalDate.of(2030, 3, 15);
        var config = new IntervalConfig("YEARLY", 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(5, rows.size()); // 2026, 2027, 2028, 2029, 2030
    }

    @Test
    @Order(14)
    void generateDatesWithStartAndEndTime() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(17, 30);
        var config = new IntervalConfig("RECURRING", 1, start, end, startTime, endTime);

        var rows = batchService.generateDates(station.id(), config, true);
        assertFalse(rows.isEmpty());
        var row = rows.getFirst();
        assertEquals(9, row.startTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(17, row.endTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(30, row.endTime().atZone(ZoneOffset.UTC).getMinute());
    }

    @Test
    @Order(15)
    void generateDatesDefaultTimesWhenNull() {
        LocalDate date = LocalDate.of(2026, 6, 1); // Monday
        var config = new IntervalConfig("RECURRING", 1, date, date, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertFalse(rows.isEmpty());
        var row = rows.getFirst();
        // Default: startTime = 00:00, endTime = 23:59
        assertEquals(0, row.startTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(23, row.endTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(59, row.endTime().atZone(ZoneOffset.UTC).getMinute());
    }

    @Test
    @Order(16)
    void generateDatesUnknownType() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        var config = new IntervalConfig("UNKNOWN_TYPE", 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertTrue(rows.isEmpty());
    }
}
