/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.BatchFieldEntry;
import dev.chojo.ember.feature.events.entity.BatchRequest;
import dev.chojo.ember.feature.events.entity.BatchRow;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.IntervalConfig;
import dev.chojo.ember.feature.events.entity.IntervalType;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
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
    private static Station station;

    @BeforeAll
    static void setup() {
        var domainEventBus = new DomainEventBus(Set.of());
        var eventServices = newEventServices(domainEventBus);
        var fieldService = new EventFieldService(
                eventFieldRepo, stationMemberRepo, memberGroupRepo, new UserTagService(userTagRepo, memberGroupRepo));
        batchService = new BatchEventService(
                eventServices.crud(), eventServices.restriction(), fieldService, eventBreakRepo, domainEventBus);

        station = stationRepo.create("BatchEventServiceStation");
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
                new BatchFieldEntry("Location", EventFieldType.STRING, EventFieldConfig.parse("{}"), true, null),
                new BatchFieldEntry("Notes", EventFieldType.STRING, EventFieldConfig.parse("{}"), false, null));

        var rows = List.of(
                new BatchRow("Event Day 1", start1, end1, Map.of("Location", "Berlin", "Notes", "Bring gear")),
                new BatchRow("Event Day 2", start2, end2, Map.of("Location", "Munich", "Notes", "Optional")));

        var request = new BatchRequest(
                "Batch Training", "Weekly batch", null, null, inlineFields, rows, false, false, null, null);

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

        var request = new BatchRequest("Default Name", "desc", null, null, List.of(), rows, false, false, null, null);

        var created = batchService.createBatch(station.id(), request);
        assertEquals(1, created.size());
        assertEquals("Default Name", created.getFirst().name());
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
                List.of(),
                rows,
                false,
                false,
                null,
                new RestrictionSelection(List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), null));

        var created = batchService.createBatch(station.id(), request);
        assertEquals(1, created.size());
        assertEquals("Restricted Event", created.getFirst().name());
    }

    @Test
    @Order(10)
    void generateDatesRecurring() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 22);
        var config = new IntervalConfig(IntervalType.RECURRING, 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(4, rows.size());
        assertTrue(rows.stream()
                .allMatch(
                        r -> r.startTime().atZone(ZoneOffset.UTC).getDayOfWeek().getValue() == 1));
    }

    @Test
    @Order(11)
    void generateDatesMonthlyFirst() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        var config = new IntervalConfig(IntervalType.MONTHLY_FIRST, 2, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(3, rows.size());
    }

    @Test
    @Order(12)
    void generateDatesQuarterly() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        var config = new IntervalConfig(IntervalType.QUARTERLY, 3, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(4, rows.size());
    }

    @Test
    @Order(13)
    void generateDatesYearly() {
        LocalDate start = LocalDate.of(2026, 3, 15);
        LocalDate end = LocalDate.of(2030, 3, 15);
        var config = new IntervalConfig(IntervalType.YEARLY, 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertEquals(5, rows.size());
    }

    @Test
    @Order(14)
    void generateDatesWithStartAndEndTime() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 1);
        LocalTime startTime = LocalTime.of(9, 0);
        LocalTime endTime = LocalTime.of(17, 30);
        var config = new IntervalConfig(IntervalType.RECURRING, 1, start, end, startTime, endTime);

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
        LocalDate date = LocalDate.of(2026, 6, 1);
        var config = new IntervalConfig(IntervalType.RECURRING, 1, date, date, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertFalse(rows.isEmpty());
        var row = rows.getFirst();
        assertEquals(0, row.startTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(23, row.endTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(59, row.endTime().atZone(ZoneOffset.UTC).getMinute());
    }

    @Test
    @Order(16)
    void generateDatesUnknownType() {
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        var config = new IntervalConfig(null, 1, start, end, null, null);

        var rows = batchService.generateDates(station.id(), config, true);
        assertTrue(rows.isEmpty());
    }
}
