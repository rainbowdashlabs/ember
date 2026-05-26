/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFieldServiceTest extends RepositoryTestBase {

    private static EventFieldService service;
    private static Station station;
    private static int eventId;
    private static int event2Id;

    @BeforeAll
    static void setup() {
        service = new EventFieldService(eventFieldRepo);
        station = stationRepo.create("EventFieldServiceStation");

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant end = start.plus(2, ChronoUnit.HOURS);
        var event = eventRepo.create(
                station.id(),
                "Field Test Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                false,
                null,
                false,
                null,
                null);
        eventId = event.id();

        var event2 = eventRepo.create(
                station.id(),
                "Field Test Event 2",
                "desc2",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                false,
                null,
                false,
                null,
                null);
        event2Id = event2.id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void replaceAndFindByEvent() {
        var fields = List.of(
                new EventFieldRepository.FieldEntry(
                        "Location", EventFieldType.STRING, "{}", "Berlin HQ", true, null, false),
                new EventFieldRepository.FieldEntry(
                        "Notes", EventFieldType.STRING, "{}", "Bring gear", false, null, true));
        service.replaceFields(eventId, fields);

        var found = service.findByEvent(eventId);
        assertEquals(2, found.size());
        assertTrue(found.stream()
                .anyMatch(f -> f.name().equals("Location") && f.value().equals("Berlin HQ") && f.overview()));
        assertTrue(found.stream()
                .anyMatch(f -> f.name().equals("Notes") && f.value().equals("Bring gear") && f.isPublic()));
    }

    @Test
    @Order(2)
    void replaceFieldsClearsOld() {
        service.replaceFields(
                eventId,
                List.of(new EventFieldRepository.FieldEntry(
                        "SingleField", EventFieldType.STRING, "{}", "value", false, null, false)));

        var found = service.findByEvent(eventId);
        assertEquals(1, found.size());
        assertEquals("SingleField", found.getFirst().name());
    }

    @Test
    @Order(3)
    void replaceFieldsWithEmpty() {
        service.replaceFields(eventId, List.of());
        assertTrue(service.findByEvent(eventId).isEmpty());
    }

    @Test
    @Order(4)
    void findDistinctFieldNames() {
        service.replaceFields(
                eventId,
                List.of(new EventFieldRepository.FieldEntry(
                        "Location", EventFieldType.STRING, "{}", "Berlin", true, null, false)));
        service.replaceFields(
                event2Id,
                List.of(
                        new EventFieldRepository.FieldEntry(
                                "Location", EventFieldType.STRING, "{}", "Munich", true, null, false),
                        new EventFieldRepository.FieldEntry(
                                "Topic", EventFieldType.STRING, "{}", "Training", false, null, false)));

        var names = service.findDistinctFieldNames(station.id());
        assertTrue(names.contains("Location"));
        assertTrue(names.contains("Topic"));
        // Location appears in both events but should only appear once
        assertEquals(1, names.stream().filter(n -> n.equals("Location")).count());
    }

    @Test
    @Order(5)
    void findOverviewFieldsByEvents() {
        // Set event1 with overview field, event2 without
        service.replaceFields(
                eventId,
                List.of(
                        new EventFieldRepository.FieldEntry("Loc", EventFieldType.STRING, "{}", "A", true, null, false),
                        new EventFieldRepository.FieldEntry(
                                "Note", EventFieldType.STRING, "{}", "B", false, null, false)));
        service.replaceFields(
                event2Id,
                List.of(new EventFieldRepository.FieldEntry(
                        "Loc", EventFieldType.STRING, "{}", "C", true, null, false)));

        var map = service.findOverviewFieldsByEvents(List.of(eventId, event2Id));
        assertTrue(map.containsKey(eventId));
        assertTrue(map.containsKey(event2Id));
        // Only overview=true fields are included
        assertEquals(1, map.get(eventId).size());
        assertEquals("Loc", map.get(eventId).getFirst().name());
        assertEquals(1, map.get(event2Id).size());
    }

    @Test
    @Order(6)
    void findOverviewFieldsByEventsEmptyList() {
        var map = service.findOverviewFieldsByEvents(List.of());
        assertTrue(map.isEmpty());
    }
}
