/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.question.FieldType;
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
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
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
                new EventFieldDraft(
                        "Location", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Berlin HQ", true, null, false),
                new EventFieldDraft(
                        "Notes", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Bring gear", false, null, true));
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
                List.of(new EventFieldDraft(
                        "SingleField",
                        FieldType.TEXT,
                        EventQuestionSettings.parse("{}"),
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
                ours.id(), "Ausbilder", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 0);
        int mine = attendanceRepo.findTemplateFields(ours.id()).getFirst().id();

        var theirs = attendanceRepo.createTemplate(station.id(), "Ein anderer Bogen");
        attendanceRepo.createTemplateField(
                theirs.id(), "Ausbilder", FieldType.TEXT, AttendanceFieldConfig.parse("{}"), 0);
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
                        new EventFieldDraft(
                                "Eigene", FieldType.TEXT, EventQuestionSettings.parse("{}"), "", false, mine, false),
                        new EventFieldDraft(
                                "Fremde",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
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
                List.of(new EventFieldDraft(
                        "Location", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Berlin", true, null, false)));
        service.replaceFields(
                event2Id,
                List.of(
                        new EventFieldDraft(
                                "Location",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "Munich",
                                true,
                                null,
                                false),
                        new EventFieldDraft(
                                "Topic",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "Training",
                                false,
                                null,
                                false)));

        var names = service.findDistinctFieldNames(station.id());
        assertTrue(names.contains("Location"));
        assertTrue(names.contains("Topic"));
        assertEquals(1, names.stream().filter(n -> n.equals("Location")).count());
    }

    @Test
    @Order(5)
    void findOverviewFieldsByEvents() {
        service.replaceFields(
                eventId,
                List.of(
                        new EventFieldDraft(
                                "Loc", FieldType.TEXT, EventQuestionSettings.parse("{}"), "A", true, null, false),
                        new EventFieldDraft(
                                "Note", FieldType.TEXT, EventQuestionSettings.parse("{}"), "B", false, null, false)));
        service.replaceFields(
                event2Id,
                List.of(new EventFieldDraft(
                        "Loc", FieldType.TEXT, EventQuestionSettings.parse("{}"), "C", true, null, false)));

        var map = service.findOverviewFieldsByEvents(List.of(eventId, event2Id));
        assertTrue(map.containsKey(eventId));
        assertTrue(map.containsKey(event2Id));
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

    /**
     * What stands in a field of an appointment is an answer like any other. A colour nobody offered
     * used to be written happily and then carried into every list and export the appointment
     * reaches.
     */
    @Test
    @Order(7)
    void aValueTheFieldDoesNotOfferIsRefused() {
        var choice = EventQuestionSettings.parse("{\"options\":[\"rot\",\"blau\"]}");

        assertThrows(
                RefusalResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft("Farbe", FieldType.CHOICE, choice, "gelb", true, null, false))));

        assertThrows(
                RefusalResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft(
                                "Tag",
                                FieldType.DATE,
                                EventQuestionSettings.parse("{}"),
                                "irgendwann",
                                true,
                                null,
                                false))));

        service.replaceFields(
                eventId, List.of(new EventFieldDraft("Farbe", FieldType.CHOICE, choice, "blau", true, null, false)));
        assertEquals("blau", service.findByEvent(eventId).getFirst().value());
    }

    /** An age counts itself from a profile, so an appointment does not take one and keeps its fields. */
    @Test
    @Order(8)
    void aTypeAnAppointmentDoesNotOfferIsRefused() {
        var before = service.findByEvent(eventId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.replaceFields(
                        eventId,
                        List.of(new EventFieldDraft(
                                "Alter", FieldType.AGE, EventQuestionSettings.empty(), "", false, null, false))));

        assertEquals(Refusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED, refused.refusal());
        assertEquals(before, service.findByEvent(eventId), "nothing was written");
    }
}
