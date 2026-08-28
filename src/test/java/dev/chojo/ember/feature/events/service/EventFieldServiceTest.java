/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.members.service.UserTagService;
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
        service = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberGroupRepo,
                new UserTagService(userTagRepo, memberGroupRepo),
                eventRepo,
                attendanceRepo);
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
                null,
                null,
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
                null,
                null,
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
                        "Location",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        "Berlin HQ",
                        true,
                        null,
                        false),
                new EventFieldRepository.FieldEntry(
                        "Notes", EventFieldType.STRING, EventFieldConfig.parse("{}"), "Bring gear", false, null, true));
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
                        "SingleField",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        "value",
                        false,
                        null,
                        false)));

        var found = service.findByEvent(eventId);
        assertEquals(1, found.size());
        assertEquals("SingleField", found.getFirst().name());
    }

    /**
     * A question of an appointment can only be tied to a field of the sheet it is taken on.
     *
     * <p>Two sheets can carry fields of the same name, and a tie into the wrong one writes the answer
     * into a sheet nobody opens. The appointment inherits its ties from the template it was made
     * from, so a template that was wrong once would keep handing the fault on.
     */
    @Test
    @Order(4)
    void aTieToAnotherSheetIsNotKept() {
        var ours = attendanceRepo.createTemplate(station.id(), "Bogen des Termins");
        attendanceRepo.createTemplateField(
                ours.id(), "Ausbilder", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        int mine = attendanceRepo.findTemplateFields(ours.id()).getFirst().id();

        var theirs = attendanceRepo.createTemplate(station.id(), "Ein anderer Bogen");
        attendanceRepo.createTemplateField(
                theirs.id(), "Ausbilder", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        int foreign = attendanceRepo.findTemplateFields(theirs.id()).getFirst().id();

        var onOurSheet = eventRepo.create(
                station.id(),
                "Termin mit Bogen",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                ours.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        service.replaceFields(
                onOurSheet.id(),
                List.of(
                        new EventFieldRepository.FieldEntry(
                                "Eigene", EventFieldType.STRING, EventFieldConfig.parse("{}"), "", false, mine, false),
                        new EventFieldRepository.FieldEntry(
                                "Fremde",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                "",
                                false,
                                foreign,
                                false)));

        var stored = service.findByEvent(onOurSheet.id());
        assertEquals(
                mine,
                stored.stream()
                        .filter(f -> f.name().equals("Eigene"))
                        .findFirst()
                        .orElseThrow()
                        .attendanceFieldId(),
                "the tie into the sheet this appointment uses is kept");
        assertNull(
                stored.stream()
                        .filter(f -> f.name().equals("Fremde"))
                        .findFirst()
                        .orElseThrow()
                        .attendanceFieldId(),
                "and the one into another sheet is not");

        eventRepo.delete(onOurSheet.id());
        attendanceRepo.deleteTemplate(ours.id());
        attendanceRepo.deleteTemplate(theirs.id());
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
                        "Location", EventFieldType.STRING, EventFieldConfig.parse("{}"), "Berlin", true, null, false)));
        service.replaceFields(
                event2Id,
                List.of(
                        new EventFieldRepository.FieldEntry(
                                "Location",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                "Munich",
                                true,
                                null,
                                false),
                        new EventFieldRepository.FieldEntry(
                                "Topic",
                                EventFieldType.STRING,
                                EventFieldConfig.parse("{}"),
                                "Training",
                                false,
                                null,
                                false)));

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
                        new EventFieldRepository.FieldEntry(
                                "Loc", EventFieldType.STRING, EventFieldConfig.parse("{}"), "A", true, null, false),
                        new EventFieldRepository.FieldEntry(
                                "Note", EventFieldType.STRING, EventFieldConfig.parse("{}"), "B", false, null, false)));
        service.replaceFields(
                event2Id,
                List.of(new EventFieldRepository.FieldEntry(
                        "Loc", EventFieldType.STRING, EventFieldConfig.parse("{}"), "C", true, null, false)));

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
