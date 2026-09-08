/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The hours the report adds up.
 *
 * <p>This is the number a station pays against, so what it does with a sheet that carries its own
 * worth is checked from the outside: the report is asked, and the column is read.
 */
class AttendanceReportServiceTest extends RepositoryTestBase {

    private static final Instant FRIDAY = Instant.parse("2026-03-06T16:00:00Z");
    private static AttendanceReportService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int groupId;
    private static int templateId;

    @BeforeAll
    static void setup() {
        service = new AttendanceReportService(
                attendanceRepo, stationMemberRepo, accountRepo, stationRepo, memberGroupRepo, new Api());
        station = stationRepo.create("Report Station");
        account = accountRepo.create("report-hours@test.com", "Report", "Reader");
        member = stationMemberRepo.create(station.id(), account.id());
        var group = memberGroupRepo.create(station.id(), "Report Group");
        groupId = group.id();
        memberGroupRepo.addMember(groupId, member.id());
        templateId =
                attendanceRepo.createTemplate(station.id(), "Report Template").id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * A sheet with one member marked present, given the times and the worth handed in.
     *
     * @return the sheet's identifier, so the caller can read its line in the report
     */
    private static int sheet(
            String title, Instant start, Instant end, Integer countedMinutes, Instant checkIn, Instant checkOut) {
        var session = attendanceRepo.createSession(templateId, start, end, null, title, countedMinutes);
        attendanceRepo.createEntry(
                session.id(),
                member.id(),
                AttendanceEntry.AttendanceStatus.PRESENT,
                AttendanceEntry.EntrySource.EXPECTED);
        var entry = attendanceRepo.findEntries(session.id()).getFirst();
        if (checkIn != null) attendanceRepo.checkIn(entry.id(), checkIn);
        if (checkOut != null) attendanceRepo.checkOut(entry.id(), checkOut);
        return session.id();
    }

    private static AttendanceReportService.SessionData lineOf(
            int sessionId, AttendanceReportService.ReportData report) {
        return report.sessions().stream()
                .filter(s -> s.sessionId() == sessionId)
                .findFirst()
                .orElseThrow();
    }

    private static AttendanceReportService.ReportData report() {
        return service.buildReport(
                station.id(),
                List.of(),
                List.of(groupId),
                FRIDAY.minus(1, ChronoUnit.DAYS),
                FRIDAY.plus(30, ChronoUnit.DAYS),
                "exact");
    }

    @Test
    void anEveningWithoutAWorthOfItsOwnCountsItsClock() {
        int sessionId = sheet("Clock", FRIDAY, FRIDAY.plus(4, ChronoUnit.HOURS), null, null, null);

        var line = lineOf(sessionId, report());

        assertNull(line.countedHours());
        assertEquals(4.0, line.entries().getFirst().hours());
    }

    @Test
    void aCountedEveningGivesItsWorthToWhoeverStayed() {
        int sessionId = sheet("Counted", FRIDAY, FRIDAY.plus(4, ChronoUnit.HOURS), 180, null, null);

        var line = lineOf(sessionId, report());

        assertEquals(3.0, line.countedHours());
        assertEquals(3.0, line.entries().getFirst().hours());
    }

    @Test
    void whoeverLeftHalfwayCountsHalfOfWhatItIsWorth() {
        int sessionId =
                sheet("Half", FRIDAY, FRIDAY.plus(4, ChronoUnit.HOURS), 180, FRIDAY, FRIDAY.plus(2, ChronoUnit.HOURS));

        var line = lineOf(sessionId, report());

        assertEquals(1.5, line.entries().getFirst().hours());
    }

    /** A weekend counts what it is worth rather than its nights, and says which days it ran over. */
    @Test
    void aWeekendCountsItsWorthAndCarriesBothDates() {
        int sessionId = sheet("Weekend", FRIDAY, FRIDAY.plus(2, ChronoUnit.DAYS), 960, null, null);

        var line = lineOf(sessionId, report());

        assertEquals(16.0, line.entries().getFirst().hours());
        assertNotNull(line.endDate());
        assertTrue(line.endDate().isAfter(line.date()));
        assertTrue(line.entries()
                .getFirst()
                .checkOut()
                .isAfter(line.entries().getFirst().checkIn()));
    }
}
