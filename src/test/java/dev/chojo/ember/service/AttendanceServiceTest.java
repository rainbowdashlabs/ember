/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceService;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
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
import java.time.temporal.ChronoUnit;
import java.util.List;

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
        service = new AttendanceService(attendanceRepo, eventRepo, eventFieldRepo, stationMemberRepo);
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

    @Test
    @Order(20)
    void createSession() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);
        var session = service.createSession(templateId, start, end, null, "Test Session");
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
        var updated = service.updateSession(sessionId, newStart, newEnd, "Updated Session");
        assertTrue(updated.isPresent());
        assertEquals("Updated Session", updated.get().title());
    }

    @Test
    @Order(25)
    void updateSessionNonExistent() {
        assertTrue(
                service.updateSession(99999, Instant.now(), Instant.now(), "X").isEmpty());
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
    }

    @Test
    @Order(32)
    void updateEntryStatus() {
        assertTrue(service.updateEntryStatus(entryId, AttendanceEntry.AttendanceStatus.PRESENT));
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
        // No event linked — should just return existing entries
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
        // Member is absent — new entry should be DECLINED
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
                null);

        // Create session linked to event — should inherit event name and times
        var session = service.createSession(templateId, null, null, event.id(), null);
        assertNotNull(session);
        assertEquals("Attend Event", session.title());
        assertNotNull(session.startTime());
        assertNotNull(session.endTime());
        assertEquals(event.id(), session.eventId());

        // Creating again with same eventId should return existing session
        var existing = service.createSession(templateId, null, null, event.id(), null);
        assertEquals(session.id(), existing.id());

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
    }

    @Test
    @Order(51)
    void createSessionWithNoTitleFallsBack() {
        // No event, no title — should fall back to template name
        var session =
                service.createSession(templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, null);
        assertNotNull(session);
        // Title should be template name since no title provided
        assertNotNull(session.title());
        service.deleteSession(session.id());
    }

    @Test
    @Order(52)
    void createSessionWithExplicitTitle() {
        var session = service.createSession(
                templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Custom Title");
        assertEquals("Custom Title", session.title());
        service.deleteSession(session.id());
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
        // No absence, no declined event — should be UNCONFIRMED
        var newSession = service.createSession(
                templateId, Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS), null, "Fresh");
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
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null);

        var account2 = accountRepo.create("attend-svc2@test.com", "Attend2", "User");
        var member2 = stationMemberRepo.create(station.id(), account2.id());

        eventRepo.createRegistration(
                event.id(),
                member2.id(),
                LocalDate.now(),
                dev.chojo.ember.feature.events.entity.EventRegistration.RegistrationStatus.ACCEPTED,
                null);

        var session = service.createSession(templateId, null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertNotNull(entries);
        assertTrue(entries.stream().anyMatch(e -> e.memberId() == member2.id()));

        service.deleteSession(session.id());
        eventRepo.delete(event.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(56)
    void syncFromEventWithDeclinedRegistration() {
        var event = eventRepo.create(
                station.id(),
                "Declined Sync",
                "",
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(3, ChronoUnit.DAYS),
                Instant.now().plus(3, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null);

        var account3 = accountRepo.create("attend-dec@test.com", "Dec", "User");
        var member3 = stationMemberRepo.create(station.id(), account3.id());

        eventRepo.createRegistration(
                event.id(),
                member3.id(),
                LocalDate.now(),
                dev.chojo.ember.feature.events.entity.EventRegistration.RegistrationStatus.DECLINED,
                null);

        var session = service.createSession(templateId, null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(
                        e -> e.memberId() == member3.id() && e.status() == AttendanceEntry.AttendanceStatus.DECLINED));

        service.deleteSession(session.id());
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
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(4, ChronoUnit.DAYS),
                Instant.now().plus(4, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null);

        var absence = service.createAbsence(
                member.id(), LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), "Sick", null);

        eventRepo.createRegistration(
                event.id(),
                member.id(),
                LocalDate.now(),
                dev.chojo.ember.feature.events.entity.EventRegistration.RegistrationStatus.ACCEPTED,
                null);

        var session = service.createSession(templateId, null, null, event.id(), null);
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream()
                .anyMatch(e -> e.memberId() == member.id() && e.status() == AttendanceEntry.AttendanceStatus.ABSENT));

        service.deleteAbsence(absence.id());
        service.deleteSession(session.id());
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
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(5, java.time.temporal.ChronoUnit.DAYS),
                Instant.now().plus(5, java.time.temporal.ChronoUnit.DAYS).plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
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

        var session = service.createSession(autoAttendTemplate.id(), null, null, event.id(), "Upgrade Session");

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
        // member starts PRESENT but is absent — sync should downgrade to ABSENT
        var account5 = accountRepo.create("attend-svc5@test.com", "Attend5", "User");
        var member5 = stationMemberRepo.create(station.id(), account5.id());

        var event = eventRepo.create(
                station.id(),
                "Absent Upgrade",
                "",
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(6, java.time.temporal.ChronoUnit.DAYS),
                Instant.now().plus(6, java.time.temporal.ChronoUnit.DAYS).plus(1, java.time.temporal.ChronoUnit.HOURS),
                templateId,
                true,
                null,
                false,
                null,
                null);

        var session = service.createSession(templateId, null, null, event.id(), null);

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
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS),
                Instant.now().plus(7, java.time.temporal.ChronoUnit.DAYS).plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null);

        // Create a template with a field
        var fieldTemplate = service.createTemplate(station.id(), "Field Default Template");
        var fields = service.createTemplateField(
                fieldTemplate.id(), "Location", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        int attendanceFieldId = fields.getFirst().id();

        // Create event field linked to attendance field — value must be valid JSON
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

        // Create session — should auto-populate from event field
        var session = service.createSession(fieldTemplate.id(), null, null, event.id(), null);
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
                dev.chojo.ember.feature.events.entity.StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(8, java.time.temporal.ChronoUnit.DAYS),
                Instant.now().plus(8, java.time.temporal.ChronoUnit.DAYS).plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null);

        eventRepo.createRegistration(
                event.id(),
                member7.id(),
                LocalDate.now(),
                dev.chojo.ember.feature.events.entity.EventRegistration.RegistrationStatus.DECLINED,
                null);

        var session = service.createSession(templateId, null, null, event.id(), null);

        // Create entry — should be DECLINED because member7 declined the event
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

        var session = service.createSession(
                autoAttendTemplate2.id(),
                Instant.now(),
                Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                "ParseTest");

        // Single number format (quoted string)
        service.setSessionFields(
                session.id(), List.of(new AttendanceFieldValueEntry(fieldId2, "\"" + member8.id() + "\"")));
        var entries = service.syncFromEvent(session.id());
        assertTrue(entries.stream().anyMatch(e -> e.memberId() == member8.id()));

        service.deleteSession(session.id());

        // Test with JSON array format with quoted numbers
        var session2 = service.createSession(
                autoAttendTemplate2.id(),
                Instant.now(),
                Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                "ParseTest2");

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
        // Template field with a default value — session creation should auto-populate
        var defaultTemplate = service.createTemplate(station.id(), "Default Value Template");
        var fieldJson = "{\"defaultValue\":\"Room 101\"}";
        service.createTemplateField(
                defaultTemplate.id(), "Room", AttendanceFieldType.STRING, AttendanceFieldConfig.parse(fieldJson), 1);
        var templateFields = service.findTemplateFields(defaultTemplate.id());

        var session = service.createSession(
                defaultTemplate.id(),
                Instant.now(),
                Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                null,
                "Defaults Session");
        assertNotNull(session);

        var sessionFields = service.findSessionFields(session.id());
        // The default value should be applied
        assertFalse(sessionFields.isEmpty());

        service.deleteSession(session.id());
        service.deleteTemplate(defaultTemplate.id());
    }

    @Test
    @Order(63)
    void createSessionNoTimeFallsBackToNow() {
        // No event, no title, no time provided — should use now()
        var session = service.createSession(templateId, null, null, null, "No Time Session");
        assertNotNull(session);
        assertNotNull(session.startTime());
        assertNotNull(session.endTime());
        service.deleteSession(session.id());
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
