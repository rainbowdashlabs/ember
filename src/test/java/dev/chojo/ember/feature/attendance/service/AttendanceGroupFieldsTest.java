/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupFieldEntry;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventDetachedDateRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static dev.chojo.ember.repository.EventGroupFixtures.part;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fields an event group hands down fill in the attendance sheet of each of its appointments: the
 * instructors entered for one appointment land on that appointment's sheet, and a value an appointment
 * replaces reaches its sheet through the group field's tie.
 */
class AttendanceGroupFieldsTest extends RepositoryTestBase {
    private static AttendanceService service;
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static final List<Integer> accounts = new ArrayList<>();

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
                new AttendanceTemplateGuards(attendanceRepo),
                new AttendanceTimeRuleRepository(),
                new EventDetachedDateRepository());
        station = stationRepo.create("Group sheet field Station");
        var annaAccount = accountRepo.create("anna@groupsheet.test", "Anna", "Sheet");
        var benAccount = accountRepo.create("ben@groupsheet.test", "Ben", "Sheet");
        accounts.add(annaAccount.id());
        accounts.add(benAccount.id());
        anna = stationMemberRepo.create(station.id(), annaAccount.id());
        ben = stationMemberRepo.create(station.id(), benAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    private static int sheetField(int sheetId, String name, FieldType type, int position) {
        service.createTemplateField(sheetId, name, type, AttendanceFieldConfig.parse("{}"), position);
        return service.findTemplateFields(sheetId).stream()
                .filter(field -> field.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    private static EventFieldDraft tied(String name, FieldType type, String value, int sheetFieldId) {
        return new EventFieldDraft(name, type, EventQuestionSettings.empty(), value, false, sheetFieldId, false);
    }

    private static EventGroup groupWith(String name, List<EventFieldDraft> fields) {
        return newEventGroupService()
                .create(
                        station.id(),
                        new EventGroupDetails(name, null, null, RegistrationDeadline.none(), false, null),
                        List.of(new EventGroupSetDraft(null, "Tage", 1)),
                        RestrictionAudience.empty(),
                        RestrictionAudience.empty(),
                        new EventGroupContent(null, fields));
    }

    private static Optional<String> onSheet(int sheetId, StationEvent part, int sheetFieldId) {
        var session = service.createSession(sheetId, null, null, part.id(), null, null);
        return service.findSessionFields(session.id()).stream()
                .filter(field -> field.fieldId() == sheetFieldId)
                .map(AttendanceSessionField::value)
                .findFirst();
    }

    @Test
    void eachAppointmentFillsInItsOwnInstructors() {
        int sheet = service.createTemplate(station.id(), "Lehrgang Ausbilder").id();
        int instructors = sheetField(sheet, "Ausbilder", FieldType.MEMBER_LIST, 0);
        var group = groupWith("Grundlehrgang", List.of(tied("Ausbilder", FieldType.MEMBER_LIST, "", instructors)));
        int groupField =
                newEventGroupService().findFields(group.id()).getFirst().id();
        var saturday = part(group, 20);
        var sunday = part(group, 21);
        eventFieldRepo.putEntry(groupField, saturday.id(), anna.id(), EventGroupFieldEntry.Kind.ENTERED);
        eventFieldRepo.putEntry(groupField, sunday.id(), ben.id(), EventGroupFieldEntry.Kind.ENTERED);

        assertEquals(
                List.of(anna.id()),
                QuestionValues.memberIds(onSheet(sheet, saturday, instructors).orElseThrow()));
        assertEquals(
                List.of(ben.id()),
                QuestionValues.memberIds(onSheet(sheet, sunday, instructors).orElseThrow()));
    }

    @Test
    void aReplacedValueReachesTheSheetThroughTheGroupsTie() {
        int sheet = service.createTemplate(station.id(), "Lehrgang Thema").id();
        int topic = sheetField(sheet, "Thema", FieldType.TEXT, 0);
        var group = groupWith("Maschinistenlehrgang", List.of(tied("Thema", FieldType.TEXT, "Pumpe", topic)));
        var monday = part(group, 22);
        var tuesday = part(group, 23);
        eventFieldRepo.replaceFields(
                tuesday.id(),
                List.of(new EventFieldDraft(
                        "Thema", FieldType.TEXT, EventQuestionSettings.empty(), "Stromerzeuger", false, null, false)));

        assertTrue(onSheet(sheet, monday, topic).orElseThrow().contains("Pumpe"));
        assertTrue(onSheet(sheet, tuesday, topic).orElseThrow().contains("Stromerzeuger"));
    }

    @Test
    void anotherSheetGetsNothingFromTheGroupsTie() {
        int sheet = service.createTemplate(station.id(), "Lehrgang Ort").id();
        int place = sheetField(sheet, "Ort", FieldType.TEXT, 0);
        int otherSheet = service.createTemplate(station.id(), "Eigene Liste").id();
        int otherPlace = sheetField(otherSheet, "Ort", FieldType.TEXT, 0);
        var group = groupWith("Sprechfunklehrgang", List.of(tied("Ort", FieldType.TEXT, "Gerätehaus", place)));
        var day = part(group, 24);

        assertTrue(onSheet(sheet, day, place).orElseThrow().contains("Gerätehaus"));
        assertTrue(onSheet(otherSheet, day, otherPlace).isEmpty());
    }
}
