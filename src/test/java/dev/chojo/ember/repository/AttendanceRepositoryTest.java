/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.AttendanceSession;
import dev.chojo.ember.entity.AttendanceTemplate;
import dev.chojo.ember.entity.MemberAbsence;
import dev.chojo.ember.entity.MemberGroup;
import dev.chojo.ember.entity.Station;
import dev.chojo.ember.entity.StationMember;
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
class AttendanceRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int templateId;
    private static int fieldId;
    private static int sessionId;
    private static int entryId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Attendance Station");
        account = accountRepo.create("attend@test.com", "Att", "Endee");
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
        AttendanceTemplate t = attendanceRepo.createTemplate(station.id(), "Weekly");
        assertNotNull(t);
        assertEquals("Weekly", t.name());
        templateId = t.id();
    }

    @Test
    @Order(2)
    void findTemplateById() {
        assertTrue(attendanceRepo.findTemplateById(templateId).isPresent());
    }

    @Test
    @Order(3)
    void findTemplatesByStation() {
        assertEquals(1, attendanceRepo.findTemplatesByStation(station.id()).size());
    }

    @Test
    @Order(4)
    void updateTemplate() {
        assertTrue(attendanceRepo.updateTemplate(templateId, "Daily"));
        assertEquals(
                "Daily",
                attendanceRepo.findTemplateById(templateId).orElseThrow().name());
    }

    // -- Template Fields --

    @Test
    @Order(10)
    void createTemplateField() {
        attendanceRepo.createTemplateField(templateId, "Notes", "text", "{}", 1);
        var fields = attendanceRepo.findTemplateFields(templateId);
        assertEquals(1, fields.size());
        assertEquals("Notes", fields.getFirst().name());
        fieldId = fields.getFirst().id();
    }

    @Test
    @Order(11)
    void updateTemplateField() {
        assertTrue(attendanceRepo.updateTemplateField(fieldId, "Comment", "text", "{}", 2));
        var fields = attendanceRepo.findTemplateFields(templateId);
        assertEquals("Comment", fields.getFirst().name());
        assertEquals(2, fields.getFirst().position());
    }

    @Test
    @Order(12)
    void deleteTemplateField() {
        assertTrue(attendanceRepo.deleteTemplateField(fieldId));
        assertTrue(attendanceRepo.findTemplateFields(templateId).isEmpty());
    }

    // -- Sessions --

    @Test
    @Order(20)
    void createSession() {
        Instant start = Instant.now();
        Instant end = start.plus(2, ChronoUnit.HOURS);
        AttendanceSession s = attendanceRepo.createSession(templateId, start, end, null, "Test Session");
        assertNotNull(s);
        assertEquals(templateId, s.templateId());
        sessionId = s.id();
    }

    @Test
    @Order(21)
    void findSessionById() {
        assertTrue(attendanceRepo.findSessionById(sessionId).isPresent());
    }

    @Test
    @Order(22)
    void findSessionsByTemplate() {
        assertEquals(1, attendanceRepo.findSessionsByTemplate(templateId).size());
    }

    @Test
    @Order(23)
    void updateSession() {
        Instant newStart = Instant.now().plus(1, ChronoUnit.DAYS);
        Instant newEnd = newStart.plus(3, ChronoUnit.HOURS);
        assertTrue(attendanceRepo.updateSession(sessionId, newStart, newEnd, "Updated"));
    }

    // -- Session Fields --

    @Test
    @Order(25)
    void setAndFindSessionField() {
        // Re-create a template field for this test
        attendanceRepo.createTemplateField(templateId, "Location", "text", "{}", 1);
        int fId = attendanceRepo.findTemplateFields(templateId).getFirst().id();

        attendanceRepo.setSessionField(sessionId, fId, "\"Room A\"");
        var fields = attendanceRepo.findSessionFields(sessionId);
        assertEquals(1, fields.size());
        assertEquals("\"Room A\"", fields.getFirst().value());

        // Upsert
        attendanceRepo.setSessionField(sessionId, fId, "\"Room B\"");
        assertEquals(
                "\"Room B\"",
                attendanceRepo.findSessionFields(sessionId).getFirst().value());

        attendanceRepo.deleteSessionField(sessionId, fId);
        assertTrue(attendanceRepo.findSessionFields(sessionId).isEmpty());
    }

    // -- Entries --

    @Test
    @Order(30)
    void createEntry() {
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.PRESENT, AttendanceEntry.EntrySource.EXPECTED);
        var entries = attendanceRepo.findEntries(sessionId);
        assertEquals(1, entries.size());
        assertEquals(
                AttendanceEntry.AttendanceStatus.PRESENT, entries.getFirst().status());
        entryId = entries.getFirst().id();
    }

    @Test
    @Order(31)
    void findEntry() {
        assertTrue(attendanceRepo.findEntry(sessionId, member.id()).isPresent());
    }

    @Test
    @Order(32)
    void findEntriesByMember() {
        assertFalse(attendanceRepo.findEntriesByMember(member.id()).isEmpty());
    }

    @Test
    @Order(33)
    void checkInAndOut() {
        Instant now = Instant.now();
        assertTrue(attendanceRepo.checkIn(entryId, now));
        assertTrue(attendanceRepo.checkOut(entryId, now.plus(1, ChronoUnit.HOURS)));
        AttendanceEntry entry = attendanceRepo.findEntry(sessionId, member.id()).orElseThrow();
        assertNotNull(entry.checkIn());
        assertNotNull(entry.checkOut());
    }

    @Test
    @Order(34)
    void deleteEntry() {
        assertTrue(attendanceRepo.deleteEntry(entryId));
        assertTrue(attendanceRepo.findEntries(sessionId).isEmpty());
    }

    // -- Update Entry Status --

    @Test
    @Order(35)
    void updateEntryStatus() {
        attendanceRepo.createEntry(
                sessionId, member.id(), AttendanceEntry.AttendanceStatus.PRESENT, AttendanceEntry.EntrySource.EXPECTED);
        var entry = attendanceRepo.findEntry(sessionId, member.id()).orElseThrow();
        assertTrue(attendanceRepo.updateEntryStatus(entry.id(), AttendanceEntry.AttendanceStatus.ABSENT));
        assertEquals(
                AttendanceEntry.AttendanceStatus.ABSENT,
                attendanceRepo.findEntry(sessionId, member.id()).orElseThrow().status());
        attendanceRepo.deleteEntry(entry.id());
    }

    // -- Template Groups --

    @Test
    @Order(40)
    void setAndFindTemplateGroups() {
        MemberGroup group1 = memberGroupRepo.create(station.id(), "Group A");
        MemberGroup group2 = memberGroupRepo.create(station.id(), "Group B");

        attendanceRepo.setTemplateGroups(
                templateId,
                List.of(
                        new AttendanceRepository.TemplateGroup(group1.id(), 1),
                        new AttendanceRepository.TemplateGroup(group2.id(), 2)));

        var groups = attendanceRepo.findTemplateGroups(templateId);
        assertEquals(2, groups.size());
        assertEquals(group1.id(), groups.get(0).groupId());
        assertEquals(1, groups.get(0).position());
        assertEquals(group2.id(), groups.get(1).groupId());
        assertEquals(2, groups.get(1).position());

        // Replace with only one group
        attendanceRepo.setTemplateGroups(templateId, List.of(new AttendanceRepository.TemplateGroup(group2.id(), 1)));
        assertEquals(1, attendanceRepo.findTemplateGroups(templateId).size());

        // Clear all
        attendanceRepo.setTemplateGroups(templateId, List.of());
        assertTrue(attendanceRepo.findTemplateGroups(templateId).isEmpty());

        memberGroupRepo.delete(group1.id());
        memberGroupRepo.delete(group2.id());
    }

    // -- Absences --

    private static int absenceId;

    @Test
    @Order(50)
    void createAbsence() {
        MemberAbsence absence = attendanceRepo.createAbsence(
                member.id(), LocalDate.now(), LocalDate.now().plusDays(7), "Vacation");
        assertNotNull(absence);
        assertEquals(member.id(), absence.memberId());
        assertEquals("Vacation", absence.reason());
        absenceId = absence.id();
    }

    @Test
    @Order(51)
    void findAbsenceById() {
        assertTrue(attendanceRepo.findAbsenceById(absenceId).isPresent());
    }

    @Test
    @Order(52)
    void findAbsencesByMember() {
        var absences = attendanceRepo.findAbsencesByMember(member.id());
        assertEquals(1, absences.size());
    }

    @Test
    @Order(53)
    void findActiveAbsencesByStation() {
        var active = attendanceRepo.findActiveAbsencesByStation(station.id());
        assertEquals(1, active.size());
    }

    @Test
    @Order(54)
    void isAbsent() {
        assertTrue(attendanceRepo.isAbsent(member.id()));
    }

    @Test
    @Order(55)
    void deleteAbsence() {
        assertTrue(attendanceRepo.deleteAbsence(absenceId));
        assertFalse(attendanceRepo.isAbsent(member.id()));
    }

    @Test
    @Order(56)
    void deleteExpiredAbsences() {
        attendanceRepo.createAbsence(
                member.id(), LocalDate.now().minusDays(2), LocalDate.now().minusDays(1), "Past");
        assertTrue(attendanceRepo.deleteExpiredAbsences());
        assertFalse(attendanceRepo.isAbsent(member.id()));
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteSession() {
        assertTrue(attendanceRepo.deleteSession(sessionId));
    }

    @Test
    @Order(99)
    void deleteTemplate() {
        assertTrue(attendanceRepo.deleteTemplate(templateId));
        assertTrue(attendanceRepo.findTemplatesByStation(station.id()).isEmpty());
    }
}
