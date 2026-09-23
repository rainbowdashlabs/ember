/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.SessionAudience;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository.TemplateGroup;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AttendanceServiceTest extends RepositoryTestBase {
    private static AttendanceService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int templateId;
    private static int sessionId;
    private static int entryId;
    private static int absenceId;

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
                stationRepo);
        station = stationRepo.create("AttendanceSvc Station");
        account = accountRepo.create("attend-svc@test.com", "Attend", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Templates --

    @Test
    @Order(1)
    void createTemplate() {
        var template = service.createTemplate(station.id(), "Weekly Meeting");
        assertNotNull(template);
        assertEquals("Weekly Meeting", template.name());
        templateId = template.id();
    }

    @Test
    @Order(2)
    void findTemplateById() {
        assertTrue(service.findTemplateById(templateId).isPresent());
        assertTrue(service.findTemplateById(99999).isEmpty());
    }

    @Test
    @Order(3)
    void findTemplatesByStation() {
        var templates = service.findTemplatesByStation(station.id());
        assertFalse(templates.isEmpty());
        assertTrue(templates.stream().anyMatch(t -> t.id() == templateId));
    }

    @Test
    @Order(4)
    void updateTemplate() {
        var updated = service.updateTemplate(templateId, "Daily Standup");
        assertTrue(updated.isPresent());
        assertEquals("Daily Standup", updated.get().name());
    }

    @Test
    @Order(5)
    void updateTemplateNonExistent() {
        assertTrue(service.updateTemplate(99999, "X").isEmpty());
    }

    // -- Template Fields --

    @Test
    @Order(10)
    void createTemplateField() {
        var fields = service.createTemplateField(
                templateId, "Location", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        assertFalse(fields.isEmpty());
        assertEquals("Location", fields.getFirst().name());
    }

    @Test
    @Order(11)
    void findTemplateFields() {
        var fields = service.findTemplateFields(templateId);
        assertFalse(fields.isEmpty());
    }

    @Test
    @Order(12)
    void updateTemplateField() {
        var fields = service.findTemplateFields(templateId);
        int fieldId = fields.getFirst().id();
        var result = service.updateTemplateField(
                templateId, fieldId, "Room", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        assertTrue(result.isPresent());
        assertEquals("Room", result.get().getFirst().name());
    }

    @Test
    @Order(13)
    void updateTemplateFieldNonExistent() {
        assertTrue(service.updateTemplateField(
                        templateId, 99999, "X", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1)
                .isEmpty());
    }

    @Test
    @Order(14)
    void deleteTemplateField() {
        var fields = service.findTemplateFields(templateId);
        int fieldId = fields.getFirst().id();
        var result = service.deleteTemplateField(templateId, fieldId);
        assertTrue(result.isPresent());
        assertTrue(result.get().isEmpty());
    }

    @Test
    @Order(15)
    void deleteTemplateFieldNonExistent() {
        assertTrue(service.deleteTemplateField(templateId, 99999).isEmpty());
    }

    // -- Template Groups --

    @Test
    @Order(16)
    void setAndFindTemplateGroups() {
        var group = memberGroupRepo.create(station.id(), "Svc Group A");
        service.setTemplateGroups(templateId, List.of(new AttendanceRepository.TemplateGroup(group.id(), 1)));
        var groups = service.findTemplateGroups(templateId);
        assertEquals(1, groups.size());
        assertEquals(group.id(), groups.getFirst().groupId());
        service.setTemplateGroups(templateId, List.of());
        memberGroupRepo.delete(group.id());
    }

    // -- Sessions --

    /** A sheet whose hours are whatever its times say, which is every sheet but the counted ones. */
    private AttendanceSession openSheet(int templateId, Instant start, Instant end, Integer eventId, String title) {
        return service.createSession(templateId, start, end, eventId, title, null);
    }

    /**
     * The day an occasion falls on, which is the day its registrations are answered for.
     *
     * <p>Read in the station's own timezone, because that is the calendar the sheet is put on. Read
     * in the server's, an appointment late enough in the day was registered for one date and looked
     * up under the next.
     */
    private LocalDate dayOf(StationEvent event) {
        return LocalDate.ofInstant(
                event.startTime(),
                StationFormat.timezoneOf(stationRepo.findById(event.stationId()).orElseThrow()));
    }

    @Test
    @Order(20)
    void createSession() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);
        var session = openSheet(templateId, start, end, null, "Test Session");
        assertNotNull(session);
        assertEquals(templateId, session.templateId());
        sessionId = session.id();
    }

    @Test
    @Order(21)
    void findSessionById() {
        assertTrue(service.findSessionById(sessionId).isPresent());
        assertTrue(service.findSessionById(99999).isEmpty());
    }

    @Test
    @Order(22)
    void findSessionsByTemplate() {
        var sessions = service.findSessionsByTemplate(templateId);
        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> s.id() == sessionId));
    }

    @Test
    @Order(23)
    void findSessionSummaries() {
        var summaries = service.findSessionSummaries(station.id());
        assertFalse(summaries.isEmpty());
    }

    @Test
    @Order(24)
    void updateSession() {
        Instant newStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(3, ChronoUnit.HOURS);
        var updated = service.updateSession(sessionId, newStart, newEnd, "Updated Session", null);
        assertTrue(updated.isPresent());
        assertEquals("Updated Session", updated.get().title());
    }

    @Test
    @Order(25)
    void updateSessionNonExistent() {
        Instant start = Instant.now();
        assertTrue(service.updateSession(99999, start, start.plus(1, ChronoUnit.HOURS), "X", null)
                .isEmpty());
    }

    // -- Session Fields --

    @Test
    @Order(26)
    void setAndFindSessionFields() {
        // Create a template field first
        service.createTemplateField(
                templateId, "Notes", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int fieldId = service.findTemplateFields(templateId).getFirst().id();

        var fields =
                service.setSessionFields(sessionId, List.of(new AttendanceFieldValueEntry(fieldId, "\"Test note\"")));
        assertFalse(fields.isEmpty());
        assertEquals("\"Test note\"", fields.getFirst().value());

        var found = service.findSessionFields(sessionId);
        assertFalse(found.isEmpty());

        // Cleanup template field
        service.deleteTemplateField(templateId, fieldId);
    }

    // -- Entries --

    @Test
    @Order(30)
    void createEntry() {
        var entries = service.createEntry(sessionId, member.id(), AttendanceEntry.EntrySource.EXPECTED);
        assertFalse(entries.isEmpty());
        assertEquals(member.id(), entries.getFirst().memberId());
        entryId = entries.getFirst().id();
    }

    @Test
    @Order(31)
    void findEntries() {
        var entries = service.findEntries(sessionId);
        assertFalse(entries.isEmpty());
        assertTrue(service.findEntryById(entries.getFirst().id()).isPresent());
        assertTrue(service.findEntryById(-1).isEmpty());
    }

    /** What an entry says is what was last written on it, whichever way it is written. */
    @Test
    @Order(32)
    void updateEntryStatus() {
        assertTrue(service.updateEntryStatus(entryId, AttendanceEntry.AttendanceStatus.PRESENT));
        assertEquals(
                AttendanceEntry.AttendanceStatus.PRESENT,
                service.findEntryById(entryId).orElseThrow().status());

        assertTrue(service.updateEntryStatus(entryId, AttendanceEntry.AttendanceStatus.ABSENT));
        assertEquals(
                AttendanceEntry.AttendanceStatus.ABSENT,
                service.findEntryById(entryId).orElseThrow().status());
    }

    @Test
    @Order(32)
    void anEntryThatIsNotThereIsNotWritten() {
        assertFalse(service.updateEntryStatus(-1, AttendanceEntry.AttendanceStatus.PRESENT));
    }

    @Test
    @Order(33)
    void checkIn() {
        assertTrue(service.checkIn(entryId, Instant.now()));
    }

    @Test
    @Order(34)
    void checkOut() {
        assertTrue(service.checkOut(entryId, Instant.now().plus(1, ChronoUnit.HOURS)));
    }

    @Test
    @Order(35)
    void resetTimes() {
        assertTrue(service.resetTimes(entryId));
    }

    @Test
    @Order(36)
    void syncFromEvent() {
        // No event linked - should just return existing entries
        var entries = service.syncFromEvent(sessionId);
        assertNotNull(entries);
    }

    @Test
    @Order(37)
    void findManagedMemberIds() {
        var ids = service.findManagedMemberIds(member.id());
        assertNotNull(ids);
    }

    @Test
    @Order(38)
    void deleteEntry() {
        assertTrue(service.deleteEntry(entryId));
        assertTrue(service.findEntries(sessionId).isEmpty());
    }

    // -- Absences --

    @Test
    @Order(40)
    void createAbsence() {
        var absence = service.createAbsence(
                member.id(), LocalDate.now(), LocalDate.now().plusDays(3), "Sick", null);
        assertNotNull(absence);
        assertEquals("Sick", absence.reason());
        absenceId = absence.id();
    }

    @Test
    @Order(41)
    void findAbsenceById() {
        assertTrue(service.findAbsenceById(absenceId).isPresent());
        assertTrue(service.findAbsenceById(99999).isEmpty());
    }

    @Test
    @Order(42)
    void findAbsencesByMember() {
        var absences = service.findAbsencesByMember(member.id());
        assertFalse(absences.isEmpty());
    }

    @Test
    @Order(43)
    void findActiveAbsencesByStation() {
        var active = service.findActiveAbsencesByStation(station.id());
        assertFalse(active.isEmpty());
    }

    @Test
    @Order(44)
    void findAbsencesByStationOnDate() {
        var absences = service.findAbsencesByStationOnDate(station.id(), LocalDate.now());
        assertFalse(absences.isEmpty());
    }

    @Test
    @Order(45)
    void createEntryWhileAbsent() {
        // Member is absent - new entry should be DECLINED
        var entries = service.createEntry(sessionId, member.id(), AttendanceEntry.EntrySource.EXPECTED);
        assertFalse(entries.isEmpty());
        assertEquals(
                AttendanceEntry.AttendanceStatus.DECLINED, entries.getFirst().status());
        service.deleteEntry(entries.getFirst().id());
    }

    @Test
    @Order(46)
    void deleteAbsence() {
        assertTrue(service.deleteAbsence(absenceId));
        assertTrue(service.findAbsenceById(absenceId).isEmpty());
    }

    // -- Cleanup --

    // -- Session with event --

    @Test
    @Order(50)
    void createSessionWithEvent() {
        // Create an event to link
        var event = eventRepo.create(
                station.id(),
                "Attend Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        // Create session linked to event - should inherit event name and times
        var session = openSheet(templateId, null, null, event.id(), null);
        assertNotNull(session);
        assertEquals("Attend Event", session.title());
        assertNotNull(session.startTime());
        assertNotNull(session.endTime());
        assertEquals(event.id(), session.eventId());

        // Creating again with same eventId should return existing session
        var existing = openSheet(templateId, null, null, event.id(), null);
        assertEquals(session.id(), existing.id());

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
    }

    /**
     * A repeating appointment carries the date somebody first configured it on, and taking that
     * date as it stood opened a sheet years in the past.
     */
    @Test
    @Order(50)
    void aSheetFromARepeatingAppointmentRunsOnTheDayItIsOpened() {
        Instant configuredLongAgo = Instant.parse("2024-09-04T18:00:00Z");
        var weekly = eventRepo.create(
                station.id(),
                "Wöchentliche Übung",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                configuredLongAgo,
                configuredLongAgo.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var session = openSheet(templateId, null, null, weekly.id(), null);

        assertEquals(
                LocalDate.now(stationRepo
                        .findById(station.id())
                        .map(StationFormat::timezoneOf)
                        .orElseThrow()),
                session.startTime().atZone(ZoneOffset.UTC).toLocalDate());
        assertEquals(18, session.startTime().atZone(ZoneOffset.UTC).getHour());
        assertEquals(Duration.ofHours(2), Duration.between(session.startTime(), session.endTime()));

        service.deleteSession(session.id());
        eventRepo.delete(weekly.id());
    }

    /**
     * Every date of a repeating appointment gets its own sheet.
     *
     * <p>The appointment is one row that comes round again and again, so a sheet was looked up by the
     * appointment alone and last week's came back: this week's attendance was written onto the sheet
     * of the first occurrence, under that occurrence's date. A sheet of the same day is still the one
     * that is handed back, because opening the same date twice must not make two.
     */
    @Test
    @Order(50)
    void everyDateOfARepeatingAppointmentGetsItsOwnSheet() {
        Instant configuredLongAgo = Instant.parse("2024-09-04T18:00:00Z");
        var weekly = eventRepo.create(
                station.id(),
                "Übungsabend",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                configuredLongAgo,
                configuredLongAgo.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var lastWeek = openSheet(
                templateId,
                Instant.now().minus(7, ChronoUnit.DAYS),
                Instant.now().minus(7, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                weekly.id(),
                "Letzte Woche");
        try {
            var today = openSheet(templateId, null, null, weekly.id(), null);
            assertNotEquals(lastWeek.id(), today.id(), "this occurrence is not last week's sheet");

            var again = openSheet(templateId, null, null, weekly.id(), null);
            assertEquals(today.id(), again.id(), "opening the same date twice makes one sheet");

            service.deleteSession(today.id());
        } finally {
            service.deleteSession(lastWeek.id());
            eventRepo.delete(weekly.id());
        }
    }

    /**
     * A sheet taken for another of a series' days is written for that day.
     *
     * <p>The day used to be whichever one the button was pressed on, because nothing carried it:
     * somebody opening next Tuesday's list on a Thursday got a sheet dated Thursday, and the
     * appointment it belonged to never had one.
     */
    @Test
    @Order(50)
    void aSheetMayBeTakenForAnotherDayOfTheSeries() {
        Instant configuredLongAgo = Instant.parse("2024-09-04T18:00:00Z");
        var weekly = eventRepo.create(
                station.id(),
                "Übungsabend",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                configuredLongAgo,
                configuredLongAgo.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var zone = ZoneId.of(station.timezone() == null ? "UTC" : station.timezone());
        LocalDate wanted = LocalDate.now(zone).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        try {
            var sheet = service.createSession(templateId, null, null, weekly.id(), null, null, null, wanted);
            try {
                assertEquals(
                        wanted,
                        sheet.startTime().atZone(zone).toLocalDate(),
                        "the sheet belongs to the day it was asked for");

                var found = service.findSessionForEvent(weekly.id(), wanted);
                assertTrue(found.isPresent(), "and the appointment finds it on that day");
                assertEquals(sheet.id(), found.get().id());

                assertTrue(
                        service.findSessionForEvent(weekly.id(), wanted.plusWeeks(1))
                                .isEmpty(),
                        "while the week after still has none");
            } finally {
                service.deleteSession(sheet.id());
            }
        } finally {
            eventRepo.delete(weekly.id());
        }
    }

    /**
     * Which day a sheet is opened for is the station's day, not the server's.
     *
     * <p>A station far enough east has been on tomorrow for hours while the server is still on today,
     * so asking the server put the sheet on the occurrence before the one everybody had turned up for,
     * and the day's sheet was nowhere to be found.
     */
    @Test
    @Order(50)
    void aSheetFromARepeatingAppointmentRunsOnTheStationsDayAndNotTheServers() {
        var faraway = ZoneId.of("Pacific/Kiritimati");
        stationRepo.updateTimezone(station.id(), faraway.getId());
        Instant configuredLongAgo = Instant.parse("2024-09-04T18:00:00Z");
        var weekly = eventRepo.create(
                station.id(),
                "Übung am anderen Ende der Welt",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                configuredLongAgo,
                configuredLongAgo.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        try {
            var session = openSheet(templateId, null, null, weekly.id(), null);

            assertEquals(
                    LocalDate.now(faraway),
                    session.startTime().atZone(ZoneOffset.UTC).toLocalDate());

            service.deleteSession(session.id());
        } finally {
            stationRepo.updateTimezone(station.id(), station.timezone());
            eventRepo.delete(weekly.id());
        }
    }

    /** A sheet of no length counted everybody who was there for nothing, so it is no longer made. */
    @Test
    @Order(50)
    void aSheetWithNoTimesRunsForTwoHours() {
        var session = openSheet(templateId, null, null, null, "Ohne Zeiten");

        assertEquals(Duration.ofHours(2), Duration.between(session.startTime(), session.endTime()));
        service.deleteSession(session.id());
    }

    @Test
    @Order(50)
    void aSheetMayRunOverSeveralDays() {
        Instant friday = Instant.parse("2026-09-04T16:00:00Z");
        var camp = openSheet(templateId, friday, friday.plus(2, ChronoUnit.DAYS), null, "Zeltlager");

        assertEquals(Duration.ofDays(2), Duration.between(camp.startTime(), camp.endTime()));
        service.deleteSession(camp.id());
    }

    @Test
    @Order(50)
    void aSheetThatEndsBeforeItStartsIsRefused() {
        Instant start = Instant.now();

        assertThrows(
                BadRequestResponse.class,
                () -> openSheet(templateId, start, start.minus(1, ChronoUnit.HOURS), null, "Rückwärts"));
    }

    @Test
    @Order(50)
    void aSheetLongerThanAMonthIsRefused() {
        Instant start = Instant.now();

        assertThrows(
                BadRequestResponse.class,
                () -> openSheet(templateId, start, start.plus(40, ChronoUnit.DAYS), null, "Zu lang"));
    }

    @Test
    @Order(50)
    void whatASheetCountsAsIsKeptAndCanBeTakenBack() {
        Instant start = Instant.now();
        var session = service.createSession(templateId, start, start.plus(4, ChronoUnit.HOURS), null, "Gewertet", 180);
        assertEquals(180, session.countedMinutes());

        var cleared = service.updateSession(session.id(), start, start.plus(4, ChronoUnit.HOURS), "Gewertet", null);
        assertTrue(cleared.isPresent());
        assertNull(cleared.get().countedMinutes());
        service.deleteSession(session.id());
    }

    @Test
    @Order(50)
    void anImpossibleNumberOfCountedHoursIsRefused() {
        Instant start = Instant.now();
        Instant end = start.plus(4, ChronoUnit.HOURS);

        assertThrows(
                BadRequestResponse.class, () -> service.createSession(templateId, start, end, null, "Negativ", -1));
        assertThrows(
                BadRequestResponse.class,
                () -> service.createSession(templateId, start, end, null, "Zu viel", 60 * 24 * 40));
    }

    @Test
    @Order(51)
    void createSessionWithNoTitleFallsBack() {
        // No event, no title - should fall back to template name
        var session = openSheet(templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, null);
        assertNotNull(session);
        // Title should be template name since no title provided
        assertNotNull(session.title());
        service.deleteSession(session.id());
    }

    @Test
    @Order(52)
    void createSessionWithExplicitTitle() {
        var session =
                openSheet(templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Custom Title");
        assertEquals("Custom Title", session.title());
        service.deleteSession(session.id());
    }

    /**
     * Only the template's groups put anybody on a sheet.
     *
     * <p>Whom the event was open to says nothing about who is expected at the appointment: the sheet
     * is the template's, and somebody outside its groups stays off it however the event was addressed.
     */
    @Test
    @Order(54)
    void theEventsAudienceDoesNotPutAnybodyOnTheSheet() {
        var invitedGroup = memberGroupRepo.create(station.id(), "Eingeladen");
        memberGroupRepo.addMember(invitedGroup.id(), member.id());

        var outsiderAccount = accountRepo.create("attend-outsider@test.com", "Drau", "Ssen");
        var outsider = stationMemberRepo.create(station.id(), outsiderAccount.id());

        var expectingTemplate = service.createTemplate(station.id(), "Nur die Eingeladenen");
        service.setTemplateGroups(expectingTemplate.id(), List.of(new TemplateGroup(invitedGroup.id(), 0)));

        var event = eventRepo.create(
                station.id(),
                "Nur für eine Gruppe",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        restrictionService.setRestrictions(
                RestrictionType.EVENT,
                event.id(),
                new RestrictionSelection(List.of(), List.of(invitedGroup.id()), List.of(), List.of(), null));

        var session = openSheet(expectingTemplate.id(), null, null, event.id(), null);
        service.syncFromEvent(session.id());

        var entries = service.findEntries(session.id());
        assertTrue(
                entries.stream().noneMatch(entry -> entry.memberId() == outsider.id()),
                "somebody outside the template's groups is not on the sheet");
        var forInvited = entries.stream()
                .filter(entry -> entry.memberId() == member.id())
                .findFirst()
                .orElseThrow(() -> new AssertionError("the template's group is missing from the sheet"));
        assertEquals(AttendanceEntry.AttendanceStatus.UNCONFIRMED, forInvited.status());

        service.deleteSession(session.id());
        service.deleteTemplate(expectingTemplate.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(outsider.id());
        accountRepo.delete(outsiderAccount.id());
        memberGroupRepo.delete(invitedGroup.id());
    }

    /**
     * An answer given on the appointment after the sheet was opened still reaches the sheet.
     */
    @Test
    @Order(54)
    void syncTakesTheAnswersOfTheEventIntoTheSheet() {
        var sheet = service.createTemplate(station.id(), "Antwort Vorlage");
        service.createTemplateField(
                sheet.id(), "Thema", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int sheetFieldId = service.findTemplateFields(sheet.id()).getFirst().id();

        var event = eventRepo.create(
                station.id(),
                "Antwort Termin",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                sheet.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var session = openSheet(sheet.id(), null, null, event.id(), null);
        assertTrue(service.findSessionFields(session.id()).stream()
                .noneMatch(field -> field.fieldId() == sheetFieldId
                        && field.value() != null
                        && !field.value().isBlank()));

        eventFieldRepo.replaceFields(
                event.id(),
                List.of(new EventFieldRepository.FieldEntry(
                        "Thema",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        "Leiterprobe",
                        false,
                        sheetFieldId,
                        false)));

        service.syncFromEvent(session.id());

        assertTrue(
                service.findSessionFields(session.id()).stream()
                        .anyMatch(field -> field.fieldId() == sheetFieldId
                                && field.value() != null
                                && field.value().contains("Leiterprobe")),
                "the answer given on the appointment is on the sheet");

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        service.deleteTemplate(sheet.id());
    }

    /**
     * An answer that begins with digits reaches the sheet as the text it is.
     *
     * <p>Read as JSON, a date is a number with the rest of the date trailing behind it, and it was
     * that trailing rest the sheet refused, taking the whole attendance down with it.
     */
    @Test
    @Order(54)
    void anAnswerThatLooksLikeANumberStillReachesTheSheet() {
        var sheet = service.createTemplate(station.id(), "Datum Vorlage");
        service.createTemplateField(
                sheet.id(), "Datum", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int sheetFieldId = service.findTemplateFields(sheet.id()).getFirst().id();

        var event = eventRepo.create(
                station.id(),
                "Datum Termin",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                sheet.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventFieldRepo.replaceFields(
                event.id(),
                List.of(new EventFieldRepository.FieldEntry(
                        "Datum",
                        EventFieldType.STRING,
                        EventFieldConfig.parse("{}"),
                        "2026-08-31",
                        false,
                        sheetFieldId,
                        false)));

        var session = openSheet(sheet.id(), null, null, event.id(), null);

        assertTrue(
                service.findSessionFields(session.id()).stream()
                        .anyMatch(field -> field.fieldId() == sheetFieldId
                                && field.value() != null
                                && field.value().contains("2026-08-31")),
                "the date is on the sheet as it was written");

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        service.deleteTemplate(sheet.id());
    }

    /**
     * A group that grew after the sheet was opened is picked up when it is filled in from the event.
     *
     * <p>Without this the newcomer stands on the sheet with nothing to mark, and the walk through the
     * open names passes them by.
     */
    @Test
    @Order(54)
    void syncPutsMembersJoinedLaterOnTheSheet() {
        var lateGroup = memberGroupRepo.create(station.id(), "Nachzügler");
        var lateTemplate = service.createTemplate(station.id(), "Nachzügler Vorlage");
        service.setTemplateGroups(lateTemplate.id(), List.of(new TemplateGroup(lateGroup.id(), 0)));

        var session = openSheet(lateTemplate.id(), Instant.now(), Instant.now(), null, "Nachzügler");
        assertTrue(service.findEntries(session.id()).isEmpty());

        memberGroupRepo.addMember(lateGroup.id(), member.id());
        var entries = service.syncFromEvent(session.id());

        assertTrue(entries.stream()
                .anyMatch(entry -> entry.memberId() == member.id()
                        && entry.status() == AttendanceEntry.AttendanceStatus.UNCONFIRMED));

        service.deleteSession(session.id());
        service.deleteTemplate(lateTemplate.id());
        memberGroupRepo.delete(lateGroup.id());
    }

    @Test
    @Order(53)
    void syncFromEventNoSession() {
        // Sync on non-existent session
        var entries = service.syncFromEvent(99999);
        assertNotNull(entries);
    }

    @Test
    @Order(54)
    void createEntryNotAbsentNotDeclined() {
        // No absence, no declined event - should be UNCONFIRMED
        var newSession = openSheet(templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Fresh");
        var entries = service.createEntry(newSession.id(), member.id(), AttendanceEntry.EntrySource.EXPECTED);
        assertFalse(entries.isEmpty());
        assertEquals(
                AttendanceEntry.AttendanceStatus.UNCONFIRMED, entries.getFirst().status());
        service.deleteSession(newSession.id());
    }

    @Test
    @Order(55)
    void syncFromEventWithRegistrations() {
        var event = eventRepo.create(
                station.id(),
                "Sync Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var account2 = accountRepo.create("attend-svc2@test.com", "Attend2", "User");
        var member2 = stationMemberRepo.create(station.id(), account2.id());

        var group = memberGroupRepo.create(station.id(), "Sync Gruppe");
        memberGroupRepo.addMember(group.id(), member2.id());
        var template = service.createTemplate(station.id(), "Sync Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        eventRegistrationRepo.create(event.id(), member2.id(), dayOf(event), RegistrationStatus.ACCEPTED, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertNotNull(entries);
        assertTrue(entries.stream()
                .anyMatch(e -> e.memberId() == member2.id() && e.status() == AttendanceEntry.AttendanceStatus.PRESENT));

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
    }

    /**
     * A member who signed up but is not expected by the template stays off the sheet.
     */
    @Test
    @Order(55)
    void syncLeavesSomebodyOutsideTheTemplatesGroupsOff() {
        var event = eventRepo.create(
                station.id(),
                "Fremde Anmeldung",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var strangerAccount = accountRepo.create("attend-stranger@test.com", "Fremd", "Ling");
        var stranger = stationMemberRepo.create(station.id(), strangerAccount.id());
        eventRegistrationRepo.create(event.id(), stranger.id(), dayOf(event), RegistrationStatus.ACCEPTED, null);

        var session = openSheet(templateId, null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream().noneMatch(e -> e.memberId() == stranger.id()));

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(stranger.id());
        accountRepo.delete(strangerAccount.id());
    }

    @Test
    @Order(56)
    void syncFromEventWithDeclinedRegistration() {
        var event = eventRepo.create(
                station.id(),
                "Declined Sync",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var account3 = accountRepo.create("attend-dec@test.com", "Dec", "User");
        var member3 = stationMemberRepo.create(station.id(), account3.id());

        var group = memberGroupRepo.create(station.id(), "Absage Gruppe");
        memberGroupRepo.addMember(group.id(), member3.id());
        var template = service.createTemplate(station.id(), "Absage Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        eventRegistrationRepo.create(event.id(), member3.id(), dayOf(event), RegistrationStatus.DECLINED, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(
                        e -> e.memberId() == member3.id() && e.status() == AttendanceEntry.AttendanceStatus.DECLINED));

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member3.id());
        accountRepo.delete(account3.id());
    }

    @Test
    @Order(57)
    void syncFromEventAbsentMember() {
        var event = eventRepo.create(
                station.id(),
                "Absent Sync",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(4, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var group = memberGroupRepo.create(station.id(), "Abwesend Gruppe");
        memberGroupRepo.addMember(group.id(), member.id());
        var template = service.createTemplate(station.id(), "Abwesend Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var absence = service.createAbsence(
                member.id(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "Sick", null);

        eventRegistrationRepo.create(event.id(), member.id(), dayOf(event), RegistrationStatus.ACCEPTED, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        // Away for the day, so the sheet settles them rather than counting on the sign-up
        assertTrue(entries.stream()
                .anyMatch(e -> e.memberId() == member.id() && e.status() != AttendanceEntry.AttendanceStatus.PRESENT));

        service.deleteAbsence(absence.id());
        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
    }

    @Test
    @Order(58)
    void syncFromEventUpgradesExistingEntryToPresent() {
        // Create event, session, and pre-add a member with UNCONFIRMED status
        var account4 = accountRepo.create("attend-svc4@test.com", "Attend4", "User");
        var member4 = stationMemberRepo.create(station.id(), account4.id());

        var event = eventRepo.create(
                station.id(),
                "Upgrade Sync",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(5, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        // Create a template field with autoAttend config
        var autoAttendTemplate = service.createTemplate(station.id(), "AutoAttend Template");
        var fieldJson = "{\"autoAttend\":true}";
        service.createTemplateField(
                autoAttendTemplate.id(),
                "Members",
                AttendanceFieldType.MEMBER,
                AttendanceFieldConfig.parse(fieldJson),
                1);
        var fields = service.findTemplateFields(autoAttendTemplate.id());
        int fieldId = fields.getFirst().id();

        var session = openSheet(autoAttendTemplate.id(), null, null, event.id(), "Upgrade Session");

        // Pre-add member4 with UNCONFIRMED status
        service.createEntry(session.id(), member4.id(), AttendanceEntry.EntrySource.EXPECTED);

        // Now set the session field value with member4's id (JSON array format)
        service.setSessionFields(
                session.id(), List.of(new AttendanceFieldValueEntry(fieldId, "[" + member4.id() + "]")));

        var entries = service.syncFromEvent(session.id());
        // member4 should have been upgraded to PRESENT
        assertTrue(entries.stream()
                .anyMatch(e -> e.memberId() == member4.id() && e.status() == AttendanceEntry.AttendanceStatus.PRESENT));

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        service.deleteTemplate(autoAttendTemplate.id());
        stationMemberRepo.delete(member4.id());
        accountRepo.delete(account4.id());
    }

    @Test
    @Order(59)
    void syncFromEventAbsenceUpdatesExistingPresent() {
        // member starts PRESENT but is absent - sync should downgrade to ABSENT
        var account5 = accountRepo.create("attend-svc5@test.com", "Attend5", "User");
        var member5 = stationMemberRepo.create(station.id(), account5.id());

        var event = eventRepo.create(
                station.id(),
                "Absent Upgrade",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(6, ChronoUnit.DAYS),
                Instant.now().plus(6, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var session = openSheet(templateId, null, null, event.id(), null);

        // Create entry as PRESENT
        service.createEntry(session.id(), member5.id(), AttendanceEntry.EntrySource.EXPECTED);
        service.updateEntryStatus(
                service.findEntries(session.id()).stream()
                        .filter(e -> e.memberId() == member5.id())
                        .findFirst()
                        .orElseThrow()
                        .id(),
                AttendanceEntry.AttendanceStatus.PRESENT);

        // Create absence
        var absence = service.createAbsence(
                member5.id(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "Ill", null);

        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(e -> e.memberId() == member5.id() && e.status() == AttendanceEntry.AttendanceStatus.ABSENT));

        service.deleteAbsence(absence.id());
        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member5.id());
        accountRepo.delete(account5.id());
    }

    @Test
    @Order(59)
    void createSessionWithEventFieldDefaultsFromLinkedEvent() {
        var account6 = accountRepo.create("attend-svc6@test.com", "Attend6", "User");
        var member6 = stationMemberRepo.create(station.id(), account6.id());

        var event = eventRepo.create(
                station.id(),
                "Field Defaults Event",
                "Event description here",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(7, ChronoUnit.DAYS),
                Instant.now().plus(7, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        // Create a template with a field
        var fieldTemplate = service.createTemplate(station.id(), "Field Default Template");
        var fields = service.createTemplateField(
                fieldTemplate.id(), "Location", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int attendanceFieldId = fields.getFirst().id();

        // Create event field linked to attendance field - value must be valid JSON
        eventFieldRepo.create(
                event.id(),
                "Location",
                EventFieldType.STRING,
                EventFieldConfig.parse("{}"),
                "\"Conference Room A\"",
                0,
                false,
                attendanceFieldId,
                false);

        // Create session - should auto-populate from event field
        var session = openSheet(fieldTemplate.id(), null, null, event.id(), null);
        assertNotNull(session);

        var sessionFields = service.findSessionFields(session.id());
        assertTrue(sessionFields.stream()
                .anyMatch(f -> f.fieldId() == attendanceFieldId && "\"Conference Room A\"".equals(f.value())));

        // Cleanup
        eventFieldRepo.deleteByEvent(event.id());
        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        service.deleteTemplate(fieldTemplate.id());
        stationMemberRepo.delete(member6.id());
        accountRepo.delete(account6.id());
    }

    @Test
    @Order(60)
    void createEntryDeclinedViaEvent() {
        // Create event, register member with DECLINED status, then create entry
        var account7 = accountRepo.create("attend-svc7@test.com", "Attend7", "User");
        var member7 = stationMemberRepo.create(station.id(), account7.id());

        var event = eventRepo.create(
                station.id(),
                "Decline Test Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(8, ChronoUnit.DAYS),
                Instant.now().plus(8, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        eventRegistrationRepo.create(event.id(), member7.id(), dayOf(event), RegistrationStatus.DECLINED, null);

        var session = openSheet(templateId, null, null, event.id(), null);

        // Create entry - should be DECLINED because member7 declined the event
        var entries = service.createEntry(session.id(), member7.id(), AttendanceEntry.EntrySource.EXPECTED);
        assertTrue(entries.stream()
                .anyMatch(
                        e -> e.memberId() == member7.id() && e.status() == AttendanceEntry.AttendanceStatus.DECLINED));

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member7.id());
        accountRepo.delete(account7.id());
    }

    @Test
    @Order(61)
    void parseMemberIdsFromFieldValueFormats() {
        // Test parseMemberIdsFromFieldValue via syncFromEvent with various formats
        var autoAttendTemplate2 = service.createTemplate(station.id(), "ParseTest Template");
        var fieldJson = "{\"autoAttend\":true}";
        service.createTemplateField(
                autoAttendTemplate2.id(),
                "Members",
                AttendanceFieldType.MEMBER,
                AttendanceFieldConfig.parse(fieldJson),
                1);
        var fields2 = service.findTemplateFields(autoAttendTemplate2.id());
        int fieldId2 = fields2.getFirst().id();

        var account8 = accountRepo.create("attend-svc8@test.com", "Attend8", "User");
        var member8 = stationMemberRepo.create(station.id(), account8.id());

        var session = openSheet(
                autoAttendTemplate2.id(), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "ParseTest");

        // Single number format (quoted string)
        service.setSessionFields(
                session.id(), List.of(new AttendanceFieldValueEntry(fieldId2, "\"" + member8.id() + "\"")));
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream().anyMatch(e -> e.memberId() == member8.id()));

        service.deleteSession(session.id());

        // Test with JSON array format with quoted numbers
        var session2 = openSheet(
                autoAttendTemplate2.id(), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "ParseTest2");

        service.setSessionFields(
                session2.id(), List.of(new AttendanceFieldValueEntry(fieldId2, "[\"" + member8.id() + "\"]")));
        var entries2 = service.syncFromEvent(session2.id());
        assertTrue(entries2.stream().anyMatch(e -> e.memberId() == member8.id()));

        service.deleteSession(session2.id());
        service.deleteTemplate(autoAttendTemplate2.id());
        stationMemberRepo.delete(member8.id());
        accountRepo.delete(account8.id());
    }

    @Test
    @Order(62)
    void createSessionWithDefaultFieldValues() {
        // Template field with a default value - session creation should auto-populate
        var defaultTemplate = service.createTemplate(station.id(), "Default Value Template");
        var fieldJson = "{\"defaultValue\":\"Room 101\"}";
        service.createTemplateField(
                defaultTemplate.id(), "Room", AttendanceFieldType.STRING, AttendanceFieldConfig.parse(fieldJson), 1);
        var templateFields = service.findTemplateFields(defaultTemplate.id());

        var session = openSheet(
                defaultTemplate.id(), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Defaults Session");
        assertNotNull(session);

        var sessionFields = service.findSessionFields(session.id());
        // The default value should be applied
        assertFalse(sessionFields.isEmpty());

        service.deleteSession(session.id());
        service.deleteTemplate(defaultTemplate.id());
    }

    @Test
    @Order(63)
    void createSessionWithEventFieldDefaultSources() {
        // Test EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME, EVENT_END_TIME sources
        var event = eventRepo.create(
                station.id(),
                "SourceDefaults Event",
                "A description",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(10, ChronoUnit.DAYS),
                Instant.now().plus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var fieldTemplate = service.createTemplate(station.id(), "Source Defaults Template");
        var nameField = service.createTemplateField(
                fieldTemplate.id(), "EventName", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int nameFieldId = nameField.getFirst().id();
        var descField = service.createTemplateField(
                fieldTemplate.id(), "EventDesc", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 2);
        int descFieldId = descField.stream()
                .filter(f -> "EventDesc".equals(f.name()))
                .findFirst()
                .orElseThrow()
                .id();
        var startField = service.createTemplateField(
                fieldTemplate.id(), "EventStart", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 3);
        int startFieldId = startField.stream()
                .filter(f -> "EventStart".equals(f.name()))
                .findFirst()
                .orElseThrow()
                .id();
        var endField = service.createTemplateField(
                fieldTemplate.id(), "EventEnd", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 4);
        int endFieldId = endField.stream()
                .filter(f -> "EventEnd".equals(f.name()))
                .findFirst()
                .orElseThrow()
                .id();

        eventFieldDefaultRepo.replaceForEvent(
                event.id(),
                List.of(
                        new EventFieldDefault(event.id(), nameFieldId, "EVENT_NAME", null),
                        new EventFieldDefault(event.id(), descFieldId, "EVENT_DESCRIPTION", null),
                        new EventFieldDefault(event.id(), startFieldId, "EVENT_START_TIME", null),
                        new EventFieldDefault(event.id(), endFieldId, "EVENT_END_TIME", null)));

        var session = openSheet(fieldTemplate.id(), null, null, event.id(), null);
        assertNotNull(session);

        var sessionFields = service.findSessionFields(session.id());
        assertTrue(sessionFields.stream()
                .anyMatch(f -> f.fieldId() == nameFieldId
                        && f.value() != null
                        && f.value().contains("SourceDefaults Event")));
        assertTrue(sessionFields.stream()
                .anyMatch(f -> f.fieldId() == descFieldId
                        && f.value() != null
                        && f.value().contains("A description")));
        assertTrue(sessionFields.stream().anyMatch(f -> f.fieldId() == startFieldId && f.value() != null));
        assertTrue(sessionFields.stream().anyMatch(f -> f.fieldId() == endFieldId && f.value() != null));

        service.deleteSession(session.id());
        eventFieldDefaultRepo.replaceForEvent(event.id(), List.of());
        eventRepo.delete(event.id());
        service.deleteTemplate(fieldTemplate.id());
    }

    @Test
    @Order(64)
    void createSessionWithGroupAutoPopulation() {
        // Template with a group - members should be auto-populated
        var group = memberGroupRepo.create(station.id(), "Auto Pop Group");
        memberGroupRepo.addMember(group.id(), member.id());

        var autoTemplate = service.createTemplate(station.id(), "Group Auto Template");
        service.setTemplateGroups(autoTemplate.id(), List.of(new AttendanceRepository.TemplateGroup(group.id(), 1)));

        var session = openSheet(
                autoTemplate.id(), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Group Session");
        var entries = service.findEntries(session.id());
        assertTrue(entries.stream().anyMatch(e -> e.memberId() == member.id()));

        service.deleteSession(session.id());
        service.setTemplateGroups(autoTemplate.id(), List.of());
        service.deleteTemplate(autoTemplate.id());
        memberGroupRepo.removeMember(group.id(), member.id());
        memberGroupRepo.delete(group.id());
    }

    /**
     * An answer still waiting for a manager is not an acceptance, so an occasion that demanded one
     * counts it as a no.
     */
    @Test
    @Order(65)
    void aPendingAnswerToADemandedRegistrationIsDeclined() {
        var account9 = accountRepo.create("attend-pending@test.com", "Pending", "User");
        var member9 = stationMemberRepo.create(station.id(), account9.id());

        var group = memberGroupRepo.create(station.id(), "Pending Gruppe");
        memberGroupRepo.addMember(group.id(), member9.id());
        var template = service.createTemplate(station.id(), "Pending Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var event = eventRepo.create(
                station.id(),
                "Pending Sync Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(9, ChronoUnit.DAYS),
                Instant.now().plus(9, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                template.id(),
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        eventRegistrationRepo.create(event.id(), member9.id(), dayOf(event), RegistrationStatus.PENDING, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(
                        e -> e.memberId() == member9.id() && e.status() == AttendanceEntry.AttendanceStatus.DECLINED));

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member9.id());
        accountRepo.delete(account9.id());
    }

    /**
     * A sheet opened for an occasion that demanded an answer arrives filled in: whoever accepted is
     * present, and whoever never answered is declined, so nobody has to look the answers up.
     */
    @Test
    @Order(65)
    void aSheetFromADemandedRegistrationArrivesFilledIn() {
        var comingAccount = accountRepo.create("attend-coming@test.com", "Kommt", "Mit");
        var coming = stationMemberRepo.create(station.id(), comingAccount.id());
        var silentAccount = accountRepo.create("attend-silent@test.com", "Sagt", "Nichts");
        var silent = stationMemberRepo.create(station.id(), silentAccount.id());

        var group = memberGroupRepo.create(station.id(), "Vorfüll Gruppe");
        memberGroupRepo.addMember(group.id(), coming.id());
        memberGroupRepo.addMember(group.id(), silent.id());
        var template = service.createTemplate(station.id(), "Vorfüll Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var event = eventRepo.create(
                station.id(),
                "Anmeldepflichtiger Abend",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(10, ChronoUnit.DAYS),
                Instant.now().plus(10, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                template.id(),
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventRegistrationRepo.create(event.id(), coming.id(), dayOf(event), RegistrationStatus.ACCEPTED, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.findEntries(session.id());
        assertEquals(
                AttendanceEntry.AttendanceStatus.PRESENT,
                entries.stream()
                        .filter(e -> e.memberId() == coming.id())
                        .findFirst()
                        .orElseThrow()
                        .status());
        assertEquals(
                AttendanceEntry.AttendanceStatus.DECLINED,
                entries.stream()
                        .filter(e -> e.memberId() == silent.id())
                        .findFirst()
                        .orElseThrow()
                        .status());

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(coming.id());
        stationMemberRepo.delete(silent.id());
        accountRepo.delete(comingAccount.id());
        accountRepo.delete(silentAccount.id());
    }

    /** Where an occasion asked nobody to answer, saying nothing leaves the row open. */
    @Test
    @Order(65)
    void silenceSettlesNothingWhereNoAnswerWasDemanded() {
        var quietAccount = accountRepo.create("attend-quiet@test.com", "Ohne", "Pflicht");
        var quiet = stationMemberRepo.create(station.id(), quietAccount.id());

        var group = memberGroupRepo.create(station.id(), "Freiwillig Gruppe");
        memberGroupRepo.addMember(group.id(), quiet.id());
        var template = service.createTemplate(station.id(), "Freiwillig Vorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var event = eventRepo.create(
                station.id(),
                "Abend ohne Anmeldung",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(11, ChronoUnit.DAYS),
                Instant.now().plus(11, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                template.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.findEntries(session.id());
        assertEquals(
                AttendanceEntry.AttendanceStatus.UNCONFIRMED,
                entries.stream()
                        .filter(e -> e.memberId() == quiet.id())
                        .findFirst()
                        .orElseThrow()
                        .status());

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(quiet.id());
        accountRepo.delete(quietAccount.id());
    }

    /** An answer given for another day of a repeating occasion has nothing to say about this sheet. */
    @Test
    @Order(65)
    void answersAreReadForTheDayTheSheetIsAbout() {
        var otherDayAccount = accountRepo.create("attend-otherday@test.com", "Anderer", "Tag");
        var otherDay = stationMemberRepo.create(station.id(), otherDayAccount.id());

        var group = memberGroupRepo.create(station.id(), "Tagesgruppe");
        memberGroupRepo.addMember(group.id(), otherDay.id());
        var template = service.createTemplate(station.id(), "Tagesvorlage");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var event = eventRepo.create(
                station.id(),
                "Abend an einem anderen Tag",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(12, ChronoUnit.DAYS),
                Instant.now().plus(12, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                template.id(),
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventRegistrationRepo.create(
                event.id(), otherDay.id(), dayOf(event).minusDays(7), RegistrationStatus.ACCEPTED, null);

        var session = openSheet(template.id(), null, null, event.id(), null);
        var entries = service.findEntries(session.id());
        assertEquals(
                AttendanceEntry.AttendanceStatus.UNCONFIRMED,
                entries.stream()
                        .filter(e -> e.memberId() == otherDay.id())
                        .findFirst()
                        .orElseThrow()
                        .status());

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(otherDay.id());
        accountRepo.delete(otherDayAccount.id());
    }

    @Test
    @Order(66)
    void syncFromEventAutoAttendNewMember() {
        // Member not already in session - autoAttend should add as PRESENT
        var account10 = accountRepo.create("attend-auto@test.com", "Auto", "New");
        var member10 = stationMemberRepo.create(station.id(), account10.id());

        var autoTemplate = service.createTemplate(station.id(), "AutoNew Template");
        var fieldJson = "{\"autoAttend\":true}";
        service.createTemplateField(
                autoTemplate.id(), "Members", AttendanceFieldType.MEMBER, AttendanceFieldConfig.parse(fieldJson), 1);
        var fields = service.findTemplateFields(autoTemplate.id());
        int fieldId = fields.getFirst().id();

        var session = openSheet(
                autoTemplate.id(), Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "AutoNew Session");

        // Set field value with member10's ID
        service.setSessionFields(
                session.id(), List.of(new AttendanceFieldValueEntry(fieldId, "[" + member10.id() + "]")));

        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(
                        e -> e.memberId() == member10.id() && e.status() == AttendanceEntry.AttendanceStatus.PRESENT));

        service.deleteSession(session.id());
        service.deleteTemplate(autoTemplate.id());
        stationMemberRepo.delete(member10.id());
        accountRepo.delete(account10.id());
    }

    @Test
    @Order(66)
    void createSessionNoTimeFallsBackToNow() {
        // No event, no title, no time provided - should use now()
        var session = openSheet(templateId, null, null, null, "No Time Session");
        assertNotNull(session);
        assertNotNull(session.startTime());
        assertNotNull(session.endTime());
        service.deleteSession(session.id());
    }

    /**
     * A sheet told whom to expect enters them instead of the template's own groups, and adds the two
     * answers up rather than crossing them.
     *
     * <p>The station kept a template with no groups on it purely to open an empty sheet. Saying who
     * belongs on this one sheet is what takes that template away.
     */
    @Test
    @Order(60)
    void aChosenAudienceEntersTypesAndGroupsTogether() {
        var group = memberGroupRepo.create(station.id(), "Jugend");
        var inGroupAccount = accountRepo.create("attend-audience-group@test.com", "Grup", "Pe");
        var inGroup = stationMemberRepo.create(station.id(), inGroupAccount.id());
        memberGroupRepo.addMember(group.id(), inGroup.id());
        stationMemberRepo.setUserType(inGroup.id(), StationUserType.TRIAL);

        var teamAccount = accountRepo.create("attend-audience-team@test.com", "Te", "Am");
        var team = stationMemberRepo.create(station.id(), teamAccount.id());
        stationMemberRepo.setUserType(team.id(), StationUserType.TEAM);

        var outsiderAccount = accountRepo.create("attend-audience-out@test.com", "Drau", "Ssen2");
        var outsider = stationMemberRepo.create(station.id(), outsiderAccount.id());
        stationMemberRepo.setUserType(outsider.id(), StationUserType.GUARDIAN);

        var template = service.createTemplate(station.id(), "Leihgabe der Felder");
        var session = service.createSession(
                template.id(),
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                "Ad hoc",
                null,
                new SessionAudience(Set.of(StationUserType.TEAM), List.of(group.id())));

        var entered = service.findEntries(session.id()).stream()
                .map(AttendanceEntry::memberId)
                .toList();
        assertTrue(entered.contains(inGroup.id()), "the group's member is on the sheet");
        assertTrue(entered.contains(team.id()), "and so is everybody of the chosen type");
        assertFalse(entered.contains(outsider.id()), "nobody either answer names is left off");

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
    }

    /**
     * Somebody a type and a group both name stands on the sheet once, and the group decides where:
     * a sheet built this way has to read like one built from a template's groups.
     */
    @Test
    @Order(61)
    void aChosenAudienceEntersNobodyTwiceAndOrdersGroupsFirst() {
        var group = memberGroupRepo.create(station.id(), "Aktive");
        var bothAccount = accountRepo.create("attend-audience-both@test.com", "Bei", "Des");
        var both = stationMemberRepo.create(station.id(), bothAccount.id());
        memberGroupRepo.addMember(group.id(), both.id());
        stationMemberRepo.setUserType(both.id(), StationUserType.TEAM);

        var typeOnlyAccount = accountRepo.create("attend-audience-type@test.com", "Nur", "Typ");
        var typeOnly = stationMemberRepo.create(station.id(), typeOnlyAccount.id());
        stationMemberRepo.setUserType(typeOnly.id(), StationUserType.TEAM);

        var template = service.createTemplate(station.id(), "Zweite Leihgabe");
        var session = service.createSession(
                template.id(),
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                "Ad hoc zwei",
                null,
                new SessionAudience(Set.of(StationUserType.TEAM), List.of(group.id())));

        var entered = service.findEntries(session.id()).stream()
                .map(AttendanceEntry::memberId)
                .toList();
        assertEquals(1, entered.stream().filter(id -> id == both.id()).count(), "named twice, entered once");
        assertTrue(
                entered.indexOf(both.id()) < entered.indexOf(typeOnly.id()),
                "whom a group names comes before whom only a type names");

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
    }

    /** Told nothing, a sheet still expects exactly whom its template expects. */
    @Test
    @Order(62)
    void anEmptyAudienceLeavesTheTemplateToDecide() {
        var group = memberGroupRepo.create(station.id(), "Vorlagengruppe");
        memberGroupRepo.addMember(group.id(), member.id());
        var template = service.createTemplate(station.id(), "Mit eigener Gruppe");
        service.setTemplateGroups(template.id(), List.of(new TemplateGroup(group.id(), 0)));

        var session = service.createSession(
                template.id(),
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                "Wie bisher",
                null,
                new SessionAudience(Set.of(), List.of()));

        assertTrue(
                service.findEntries(session.id()).stream().anyMatch(entry -> entry.memberId() == member.id()),
                "the template's own group still fills the sheet");

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        memberGroupRepo.delete(group.id());
    }

    /**
     * A group of another station is dropped rather than obeyed. Which groups exist is the screen's
     * business, and a sheet is not the place to reach into another station's members.
     */
    @Test
    @Order(63)
    void aGroupOfAnotherStationNamesNobody() {
        var elsewhere = stationRepo.create("AttendanceSvc Fremde Wache");
        var foreignGroup = memberGroupRepo.create(elsewhere.id(), "Fremde Gruppe");
        var foreignAccount = accountRepo.create("attend-audience-foreign@test.com", "Frem", "De");
        var foreign = stationMemberRepo.create(elsewhere.id(), foreignAccount.id());
        memberGroupRepo.addMember(foreignGroup.id(), foreign.id());

        var template = service.createTemplate(station.id(), "Dritte Leihgabe");
        var session = service.createSession(
                template.id(),
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                "Ad hoc drei",
                null,
                new SessionAudience(Set.of(), List.of(foreignGroup.id())));

        assertTrue(service.findEntries(session.id()).isEmpty(), "nobody of the other station is reached");

        service.deleteSession(session.id());
        service.deleteTemplate(template.id());
        stationRepo.delete(elsewhere.id());
        accountRepo.delete(foreignAccount.id());
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteSession() {
        assertTrue(service.deleteSession(sessionId));
        assertTrue(service.findSessionById(sessionId).isEmpty());
    }

    @Test
    @Order(99)
    void deleteTemplate() {
        assertTrue(service.deleteTemplate(templateId));
        assertTrue(service.findTemplateById(templateId).isEmpty());
    }
}
