/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.events.entity.AppointmentTemplateFieldDraft;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFieldDefaultService;
import dev.chojo.ember.feature.events.service.EventTemplateService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deleting a field of an attendance template archives it: the answers given to it stay on their
 * sheets, and only what would use it for new work lets go of it.
 */
class AttendanceFieldArchiveTest extends RepositoryTestBase {
    private static final Instant START = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.HOURS);
    private static final Instant END = START.plus(2, ChronoUnit.HOURS);

    private static AttendanceService service;
    private static EventCrudService eventCrud;
    private static EventFieldDefaultService fieldDefaults;
    private static EventTemplateService eventTemplates;
    private static EventTemplateRepository eventTemplateRepo;
    private static Station station;

    private int templateId;
    private int topicId;
    private int leaderId;
    private int noteId;
    private int sheetId;

    @BeforeAll
    static void setup() {
        service = new AttendanceService(
                attendanceRepo,
                eventRepo,
                eventFieldRepo,
                eventFieldDefaultRepo,
                eventRegistrationRepo,
                stationMemberRepo,
                memberGroupRepo,
                new Attendance(),
                stationRepo,
                eventDateCancellationRepo,
                new AttendanceAudienceService(attendanceRepo),
                memberEligibility,
                new AttendanceTemplateGuards(attendanceRepo));
        var events = newEventServices(new DomainEventBus(Set.of()));
        eventCrud = events.crud();
        fieldDefaults = events.fieldDefault();
        eventTemplateRepo = new EventTemplateRepository();
        eventTemplates = new EventTemplateService(
                eventTemplateRepo, attendanceRepo, memberEligibility, new AttendanceTemplateGuards(attendanceRepo));
        station = stationRepo.create("Field Archive Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    /** A template with three fields in order, and one sheet that answered all of them. */
    @BeforeEach
    void answeredSheet() {
        templateId = service.createTemplate(station.id(), "Dienstabend " + System.nanoTime())
                .id();
        topicId = fieldNamed(service.createTemplateField(templateId, "Thema", FieldType.TEXT, empty(), 0), "Thema");
        leaderId =
                fieldNamed(service.createTemplateField(templateId, "Leitung", FieldType.TEXT, empty(), 1), "Leitung");
        noteId = fieldNamed(service.createTemplateField(templateId, "Notiz", FieldType.TEXT, empty(), 2), "Notiz");
        sheetId =
                service.createSession(templateId, START, END, null, null, null).id();
        service.setSessionFields(
                sheetId,
                List.of(
                        new AttendanceFieldValueEntry(topicId, "\"Knoten\""),
                        new AttendanceFieldValueEntry(leaderId, "\"Greta\""),
                        new AttendanceFieldValueEntry(noteId, "\"Regen\"")));
    }

    @Test
    void theSheetStillShowsAndPrintsTheAnswerInItsPlace() {
        service.archiveTemplateField(templateId, leaderId);

        var shown = service.findSheetFields(sheetId);
        assertEquals(List.of(topicId, leaderId, noteId), ids(shown));
        var lines = AttendanceExportService.fieldLines(shown, answers(sheetId), Map.of(), "de");
        assertTrue(lines.contains(new AttendanceExportService.NameValue("Leitung", "Greta")));
    }

    @Test
    void theRemainingFieldsKeepTheirOrder() {
        service.archiveTemplateField(templateId, leaderId);

        assertEquals(List.of(topicId, noteId), ids(service.findTemplateFields(templateId)));
        int newSheet = service.createSession(templateId, START.plus(1, ChronoUnit.DAYS), null, null, null, null)
                .id();
        assertEquals(List.of(topicId, noteId), ids(service.findSheetFields(newSheet)));
    }

    /** A new sheet ignores an answer sent for the deleted field, since it does not show it. */
    @Test
    void aNewSheetTakesNoAnswerForADeletedField() {
        service.archiveTemplateField(templateId, leaderId);
        int newSheet = service.createSession(templateId, START.plus(2, ChronoUnit.DAYS), null, null, null, null)
                .id();

        service.setSessionFields(newSheet, List.of(new AttendanceFieldValueEntry(leaderId, "\"Otto\"")));

        assertTrue(service.findSessionFields(newSheet).stream().noneMatch(field -> field.fieldId() == leaderId));
    }

    @Test
    void aDeletedFieldsNameIsFreeAgain() {
        service.archiveTemplateField(templateId, leaderId);

        var fields = service.createTemplateField(templateId, "Leitung", FieldType.TEXT, empty(), 1);

        assertTrue(fieldNamed(fields, "Leitung") != leaderId);
    }

    @Test
    void changingADeletedFieldIsRefused() {
        service.archiveTemplateField(templateId, leaderId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> service.updateTemplateField(templateId, leaderId, "Leitung", FieldType.TEXT, empty(), 1));
        assertEquals("AT-059", refused.refusal().code());
        var again = assertThrows(RefusalResponse.class, () -> service.archiveTemplateField(templateId, leaderId));
        assertEquals("AT-059", again.refusal().code());
    }

    /**
     * An appointment's starting value for the field goes, and appointment and appointment template
     * questions tied to it are untied, as they were when a field was really deleted.
     */
    @Test
    void whatPointedAtTheFieldForNewWorkLetsGo() {
        int eventId = appointment();
        fieldDefaults.setForEvent(eventId, List.of(new EventFieldDefault(eventId, leaderId, "VALUE", "Greta")));
        eventFieldRepo.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        "Leitung", FieldType.TEXT, EventQuestionSettings.parse("{}"), "", false, leaderId, false)));
        int eventTemplate = eventTemplates
                .create(station.id(), "Dienst " + System.nanoTime())
                .id();
        eventTemplates.update(
                eventTemplate, "Dienst", null, null, null, null, null, null, null, null, templateId, null);
        eventTemplateRepo.replaceFields(
                eventTemplate,
                List.of(new AppointmentTemplateFieldDraft(
                        "Leitung",
                        FieldType.TEXT,
                        EventQuestionSettings.parse("{}"),
                        0,
                        false,
                        false,
                        leaderId,
                        null)));

        service.archiveTemplateField(templateId, leaderId);

        assertTrue(fieldDefaults.findByEvent(eventId).isEmpty());
        assertNull(eventFieldRepo.findByEvent(eventId).getFirst().attendanceFieldId());
        assertNull(eventTemplateRepo.findFields(eventTemplate).getFirst().attendanceFieldId());
    }

    @Test
    void aStartingValueForADeletedFieldIsRefused() {
        int eventId = appointment();
        service.archiveTemplateField(templateId, leaderId);

        var refused = assertThrows(
                RefusalResponse.class,
                () -> fieldDefaults.setForEvent(
                        eventId, List.of(new EventFieldDefault(eventId, leaderId, "VALUE", "Greta"))));
        assertEquals("AT-059", refused.refusal().code());
    }

    /** A station that is deleted still takes its fields with it, answers and all. */
    @Test
    void aDeletedStationTakesItsFieldsAndAnswersWithIt() {
        var leaving = stationRepo.create("Field Archive Leaving Station");
        int template = service.createTemplate(leaving.id(), "Abschied").id();
        int field = fieldNamed(service.createTemplateField(template, "Thema", FieldType.TEXT, empty(), 0), "Thema");
        int sheet =
                service.createSession(template, START, END, null, null, null).id();
        service.setSessionFields(sheet, List.of(new AttendanceFieldValueEntry(field, "\"Abschied\"")));
        service.archiveTemplateField(template, field);

        assertTrue(stationRepo.delete(leaving.id()));

        assertTrue(service.findSessionById(sheet).isEmpty());
        assertTrue(service.findTemplateById(template).isEmpty());
    }

    private int appointment() {
        return eventCrud
                .create(
                        station.id(),
                        "Übungsabend",
                        null,
                        StationEvent.EventType.ONE_TIME,
                        null,
                        START,
                        END,
                        templateId,
                        false,
                        null,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null)
                .id();
    }

    private static Map<Integer, String> answers(int sheet) {
        return service.findSessionFields(sheet).stream()
                .collect(Collectors.toMap(AttendanceSessionField::fieldId, AttendanceSessionField::value));
    }

    private static List<Integer> ids(List<AttendanceTemplateField> fields) {
        return fields.stream().map(AttendanceTemplateField::id).toList();
    }

    private static int fieldNamed(List<AttendanceTemplateField> fields, String name) {
        return fields.stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static AttendanceFieldConfig empty() {
        return AttendanceFieldConfig.parse("{}");
    }
}
