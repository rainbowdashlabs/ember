/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.knowledgebase.service.KbFileStorageService;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.ProfileFieldType;
import dev.chojo.ember.feature.members.entity.StationMember;
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
import static org.mockito.Mockito.mock;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GdprExportServiceTest extends RepositoryTestBase {
    private static GdprExportService gdprService;
    private static Account account;
    private static int stationId;
    private static StationMember member;

    @Test
    @Order(1)
    void setup() {
        gdprService = new GdprExportService(
                accountRepo, stationMemberRepo, memberLookupService, mock(KbFileStorageService.class));

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
                account.id(),
                "test-token-gdpr",
                Instant.now().plus(1, ChronoUnit.HOURS),
                "Mozilla/5.0 Test Agent",
                null);

        // Create profile field + value
        var field = profileFieldRepo.create(
                stationId,
                "Telefon",
                ProfileFieldType.TEXT,
                ProfileFieldConfig.parse("{}"),
                0,
                ProfileFieldScope.MEMBER);
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
                null,
                null,
                null,
                null,
                null);
        eventRegistrationRepo.create(event.id(), member.id(), LocalDate.now(), RegistrationStatus.ACCEPTED, null);

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
    void exportAccountDataContainsSessionsViaAccountTables() {
        var data = gdprService.exportAccountData(account.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("accountTables");
        assertNotNull(tables);
        var sessions = tables.get("account_session");
        assertNotNull(sessions, "account_session should be in accountTables for an ACCOUNT_ID identity");
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
    void exportMemberDataContainsProfileFieldValues() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("memberTables");
        assertNotNull(tables);
        var values = tables.get("profile_field_value");
        assertNotNull(values, "profile_field_value should be in memberTables");
        assertFalse(values.isEmpty());
    }

    @Test
    @Order(21)
    void exportMemberDataContainsGroupMembership() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("memberTables");
        var groups = tables.get("member_group_entry");
        assertNotNull(groups);
        assertFalse(groups.isEmpty());
    }

    @Test
    @Order(22)
    void exportMemberDataContainsAttendance() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("memberTables");
        var attendance = tables.get("attendance_entry");
        assertNotNull(attendance);
        assertFalse(attendance.isEmpty());
        assertTrue(attendance.stream().anyMatch(a -> "PRESENT".equals(a.get("status"))));
    }

    @Test
    @Order(23)
    void exportMemberDataContainsEventRegistrations() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("memberTables");
        var regs = tables.get("event_registration");
        assertNotNull(regs);
        assertFalse(regs.isEmpty());
    }

    @Test
    @Order(24)
    void exportMemberDataContainsAbsences() {
        var data = gdprService.exportMemberData(member.id());
        @SuppressWarnings("unchecked")
        var tables = (Map<String, List<Map<String, Object>>>) data.get("memberTables");
        var absences = tables.get("member_absence");
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
    void exportMemberDataContainsExpectedEnvelopeKeys() {
        var data = gdprService.exportMemberData(member.id());
        // After the metadata-driven rewrite the envelope is:
        //   memberId, stationId, former, stationName, memberTables, memberUidTables
        // The actual per-table payload lives under memberTables (keyed by DB table name) and
        // is driven by gdprExport.identityColumns in data_tracking.json.
        for (var key : List.of("memberId", "stationId", "former", "memberTables", "memberUidTables")) {
            assertTrue(data.containsKey(key), "Missing envelope key: " + key);
        }
    }
}
