/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.Account;
import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.EventRegistration;
import dev.chojo.ember.entity.ProfileFieldScope;
import dev.chojo.ember.entity.StationEvent;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GdprExportServiceTest extends RepositoryTestBase {
    private static GdprExportService gdprService;
    private static Account account;
    private static int stationId;
    private static StationMember member;

    @Test
    @Order(1)
    void setup() {
        gdprService = new GdprExportService(accountRepo, stationMemberRepo);

        // Create account
        account = accountRepo.create("gdpr-test@example.com", "Max", "Mustermann", true);
        assertNotNull(account);

        // Create station
        var station = stationRepo.create("Teststation");
        stationId = station.id();

        // Create member
        member = stationMemberRepo.create(stationId, account.id());
        assertNotNull(member);

        // Create session
        accountRepo.createSession(
                account.id(), "test-token-gdpr", Instant.now().plus(1, ChronoUnit.HOURS), "Mozilla/5.0 Test Agent");

        // Create profile field + value
        var field = profileFieldRepo.create(stationId, "Telefon", "TEXT", "{}", 0, ProfileFieldScope.MEMBER);
        profileFieldRepo.setValue(member.id(), field.id(), "\"0151 12345678\"");

        // Create group + assign member
        var group = memberGroupRepo.create(stationId, "Anfänger");
        memberGroupRepo.addMember(group.id(), member.id());

        // Create attendance session + entry
        var template = attendanceRepo.createTemplate(stationId, "Test Template");
        var session = attendanceRepo.createSession(
                template.id(), Instant.now().minus(1, ChronoUnit.HOURS), Instant.now(), null, "Test Session");
        attendanceRepo.createEntry(
                session.id(),
                member.id(),
                AttendanceEntry.AttendanceStatus.PRESENT,
                AttendanceEntry.EntrySource.EXPECTED);

        // Create event + registration
        var event = eventRepo.create(
                stationId,
                "Test Event",
                "Description",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now().plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null);
        eventRepo.createRegistration(
                event.id(), member.id(), LocalDate.now(), EventRegistration.RegistrationStatus.ACCEPTED, null);

        // Create absence
        attendanceRepo.createAbsence(
                member.id(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), "Urlaub", null);
    }

    @Test
    @Order(10)
    void exportAccountDataContainsAccount() {
        var data = gdprService.exportAccountData(account.id());
        assertNotNull(data);
        assertEquals("GDPR/DSGVO Data Export", data.get("exportType"));

        @SuppressWarnings("unchecked")
        var accountData = (Map<String, Object>) data.get("account");
        assertNotNull(accountData);
        assertEquals("gdpr-test@example.com", accountData.get("email"));
        assertEquals("Max", accountData.get("firstName"));
        assertEquals("Mustermann", accountData.get("lastName"));
    }

    @Test
    @Order(11)
    void exportAccountDataContainsSessions() {
        var data = gdprService.exportAccountData(account.id());
        @SuppressWarnings("unchecked")
        var sessions = (List<Map<String, Object>>) data.get("sessions");
        assertNotNull(sessions);
        assertFalse(sessions.isEmpty());
        assertTrue(sessions.stream().anyMatch(s -> "Mozilla/5.0 Test Agent".equals(s.get("user_agent"))));
    }

    @Test
    @Order(12)
    void exportAccountDataContainsStationMemberships() {
        var data = gdprService.exportAccountData(account.id());
        @SuppressWarnings("unchecked")
        var memberships = (List<Map<String, Object>>) data.get("stationMemberships");
        assertNotNull(memberships);
        assertEquals(1, memberships.size());

        var membership = memberships.getFirst();
        assertEquals(member.id(), membership.get("memberId"));
        assertEquals(stationId, membership.get("stationId"));
    }

    @Test
    @Order(20)
    void exportMemberDataContainsProfileFields() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var fields = (List<Map<String, Object>>) data.get("profileFields");
        assertNotNull(fields);
        assertFalse(fields.isEmpty());
        assertTrue(fields.stream().anyMatch(f -> "Telefon".equals(f.get("name"))));
    }

    @Test
    @Order(21)
    void exportMemberDataContainsGroups() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var groups = (List<Map<String, Object>>) data.get("groups");
        assertNotNull(groups);
        assertFalse(groups.isEmpty());
        assertTrue(groups.stream().anyMatch(g -> "Anfänger".equals(g.get("name"))));
    }

    @Test
    @Order(22)
    void exportMemberDataContainsAttendance() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var attendance = (List<Map<String, Object>>) data.get("attendance");
        assertNotNull(attendance);
        assertFalse(attendance.isEmpty());
        assertTrue(attendance.stream().anyMatch(a -> "PRESENT".equals(a.get("status"))));
    }

    @Test
    @Order(23)
    void exportMemberDataContainsEventRegistrations() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var regs = (List<Map<String, Object>>) data.get("eventRegistrations");
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> "Test Event".equals(r.get("event_name"))));
    }

    @Test
    @Order(24)
    void exportMemberDataContainsAbsences() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var absences = (List<Map<String, Object>>) data.get("absences");
        assertNotNull(absences);
        assertFalse(absences.isEmpty());
        assertTrue(absences.stream().anyMatch(a -> "Urlaub".equals(a.get("reason"))));
    }

    @Test
    @Order(30)
    void exportMemberDataReturnsEmptyForNonexistentMember() {
        var data = gdprService.exportMemberData(99999);
        assertTrue(data.isEmpty());
    }

    @Test
    @Order(31)
    void exportAccountDataForNonexistentAccountReturnsMinimalData() {
        var data = gdprService.exportAccountData(99999);
        assertNotNull(data);
        assertNull(data.get("account"));
        @SuppressWarnings("unchecked")
        var memberships = (List<?>) data.get("stationMemberships");
        assertTrue(memberships.isEmpty());
    }

    @Test
    @Order(40)
    void exportMemberDataContainsAllExpectedKeys() {
        var data = gdprService.exportMemberData(member.id());
        var expectedKeys = List.of(
                "memberId",
                "stationId",
                "former",
                "stationName",
                "roles",
                "profileFields",
                "groups",
                "tags",
                "managedBy",
                "manages",
                "attendance",
                "eventRegistrations",
                "absences",
                "inventoryItems",
                "inventoryHistory",
                "exchangeRequests",
                "procurementRequests",
                "formResponses",
                "notifications",
                "newsAuthored",
                "newsComments",
                "profileFieldChanges",
                "notificationSettings");
        for (var key : expectedKeys) {
            assertTrue(data.containsKey(key), "Missing key: " + key);
        }
    }
}
