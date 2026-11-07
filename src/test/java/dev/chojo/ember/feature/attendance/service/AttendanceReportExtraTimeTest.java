/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.TimeRuleInput;
import dev.chojo.ember.feature.attendance.repository.AttendanceTimeRuleRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.TagVisibility;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.CsvWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preparation and follow-up in the hours a report adds up: credited to the people a sheet's rules
 * name, added before the rounding, on top of a sheet's own worth, and left out for somebody marked as
 * not having done it.
 */
class AttendanceReportExtraTimeTest extends RepositoryTestBase {
    private static final Instant EVENING = Instant.parse("2026-04-07T17:00:00Z");
    private static final AttendanceTimeRuleRepository timeRules = new AttendanceTimeRuleRepository();

    private static AttendanceReportService service;
    private static Station station;
    private static Account leaderAccount;
    private static Account memberAccount;
    private static StationMember leader;
    private static StationMember plain;
    private static int leaders;
    private static int everybody;
    private static int templateId;

    @BeforeAll
    static void setup() {
        service = new AttendanceReportService(
                attendanceRepo,
                stationMemberRepo,
                accountRepo,
                stationRepo,
                memberGroupRepo,
                new Api(),
                newStationLogoService(),
                timeRules,
                restrictionService);
        station = stationRepo.create("Extra Time Station");
        leaderAccount = accountRepo.create("extra-leader@test.com", "Lea", "Leitung");
        memberAccount = accountRepo.create("extra-member@test.com", "Max", "Mitglied");
        leader = stationMemberRepo.create(station.id(), leaderAccount.id());
        plain = stationMemberRepo.create(station.id(), memberAccount.id());
        leaders = memberGroupRepo.create(station.id(), "Jugendwarte").id();
        everybody = memberGroupRepo.create(station.id(), "Alle").id();
        memberGroupRepo.addMember(leaders, leader.id());
        memberGroupRepo.addMember(everybody, leader.id());
        memberGroupRepo.addMember(everybody, plain.id());
        templateId =
                attendanceRepo.createTemplate(station.id(), "Gruppenstunde").id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(leaderAccount.id());
        accountRepo.delete(memberAccount.id());
    }

    private static RestrictionAudience group(int groupId) {
        return new RestrictionAudience(List.of(), List.of(groupId), List.of(), List.of(), RestrictionMode.AND);
    }

    /** A sheet of 90 minutes on the given day after the first evening, with both people present. */
    private static int sheet(int dayOffset, Integer countedMinutes, List<TimeRuleInput> rules) {
        Instant start = EVENING.plus(dayOffset, ChronoUnit.DAYS);
        var session = attendanceRepo.createSession(
                templateId, start, start.plus(90, ChronoUnit.MINUTES), null, "Abend " + dayOffset, countedMinutes);
        for (StationMember present : List.of(leader, plain)) {
            attendanceRepo.createEntry(
                    session.id(),
                    present.id(),
                    AttendanceEntry.AttendanceStatus.PRESENT,
                    AttendanceEntry.EntrySource.EXPECTED);
        }
        timeRules.replaceSessionRules(session.id(), rules);
        return session.id();
    }

    private static AttendanceReportService.SessionMemberEntry entryOf(
            AttendanceReportService.ReportData report, int sessionId, StationMember member) {
        return report.sessions().stream()
                .filter(s -> s.sessionId() == sessionId)
                .findFirst()
                .orElseThrow()
                .entries()
                .stream()
                .filter(e -> e.memberId() == member.id())
                .findFirst()
                .orElseThrow();
    }

    private static AttendanceReportService.ReportData report(int dayOffset, String rounding) {
        Instant day = EVENING.plus(dayOffset, ChronoUnit.DAYS);
        return service.buildReport(
                station.id(),
                List.of(),
                List.of(everybody),
                day.minus(1, ChronoUnit.HOURS),
                day.plus(1, ChronoUnit.DAYS),
                rounding);
    }

    @Test
    void theNamedAreCreditedAndEverybodyElseCountsTheClock() {
        int sessionId = sheet(0, null, List.of(new TimeRuleInput("Jugendwart", 30, 15, group(leaders))));

        var report = report(0, "exact");

        var credited = entryOf(report, sessionId, leader);
        assertEquals(2.25, credited.hours(), 0.001);
        assertEquals(30, credited.preparationMinutes());
        assertEquals(15, credited.followUpMinutes());
        var notNamed = entryOf(report, sessionId, plain);
        assertEquals(1.5, notNamed.hours(), 0.001);
        assertEquals(0, notNamed.preparationMinutes());
    }

    @Test
    void theMinutesAreRoundedTogetherWithThePresence() {
        int sessionId = sheet(1, null, List.of(new TimeRuleInput("Jugendwart", 20, 0, group(leaders))));

        assertEquals(2.0, entryOf(report(1, "ceil"), sessionId, leader).hours(), 0.001, "1:50 rounds up to 2");
        assertEquals(2.0, entryOf(report(1, "round"), sessionId, leader).hours(), 0.001, "1:50 rounds to 2");
        assertEquals(1.83, entryOf(report(1, "exact"), sessionId, leader).hours(), 0.001);
    }

    @Test
    void theMinutesComeOnTopOfASheetsOwnWorth() {
        int sessionId = sheet(2, 180, List.of(new TimeRuleInput("Jugendwart", 30, 30, group(leaders))));

        assertEquals(4.0, entryOf(report(2, "exact"), sessionId, leader).hours(), 0.001);
        assertEquals(3.0, entryOf(report(2, "exact"), sessionId, plain).hours(), 0.001);
    }

    @Test
    void theLargestOfSeveralRulesCounts() {
        int sessionId = sheet(
                3,
                null,
                List.of(
                        new TimeRuleInput("Jugendwart", 30, 0, group(leaders)),
                        new TimeRuleInput("Alle", 10, 20, group(everybody))));

        var credited = entryOf(report(3, "exact"), sessionId, leader);

        assertEquals(30, credited.preparationMinutes());
        assertEquals(20, credited.followUpMinutes());
        assertEquals(10, entryOf(report(3, "exact"), sessionId, plain).preparationMinutes());
    }

    @Test
    void somebodyMarkedAsNotHavingDoneItCountsThePresenceAlone() {
        int sessionId = sheet(4, null, List.of(new TimeRuleInput("Jugendwart", 30, 15, group(leaders))));
        var entry = attendanceRepo.findEntry(sessionId, leader.id()).orElseThrow();
        attendanceRepo.setSkipExtraTime(entry.id(), true);

        var marked = entryOf(report(4, "exact"), sessionId, leader);

        assertEquals(1.5, marked.hours(), 0.001);
        assertEquals(0, marked.preparationMinutes());
    }

    @Test
    void aSheetWithoutRulesCountsAsBefore() {
        int sessionId = sheet(5, null, List.of());

        assertEquals(1.5, entryOf(report(5, "exact"), sessionId, leader).hours(), 0.001);
    }

    @Test
    void theTotalsAndTheSpreadsheetCarryTheMinutes() {
        sheet(6, null, List.of(new TimeRuleInput("Jugendwart", 30, 15, group(leaders))));

        var summary = report(6, "exact").members().stream()
                .filter(m -> m.memberId() == leader.id())
                .findFirst()
                .orElseThrow();
        assertEquals(30, summary.preparationMinutes());
        assertEquals(15, summary.followUpMinutes());

        Instant day = EVENING.plus(6, ChronoUnit.DAYS);
        var csv = service.exportReportCsv(
                        station.id(),
                        List.of(),
                        List.of(everybody),
                        day.minus(1, ChronoUnit.HOURS),
                        day.plus(1, ChronoUnit.DAYS),
                        "exact",
                        "custom",
                        CsvWriter.Separator.SEMICOLON)
                .orElseThrow();
        String text = new String(csv.bytes(), StandardCharsets.UTF_8);
        assertTrue(text.contains("Vorbereitung (Min.)"), text);
        assertTrue(text.contains("Nachbereitung (Min.)"), text);
        assertTrue(text.contains(";30;15"), text);
    }

    @Test
    void bothPrintoutsRenderTheMinutesInEitherLanguage() {
        sheet(7, null, List.of(new TimeRuleInput("Jugendwart", 30, 15, group(leaders))));
        Instant day = EVENING.plus(7, ChronoUnit.DAYS);

        for (String locale : List.of("de-DE", "en")) {
            stationRepo.updateLocale(station.id(), locale);
            for (String period : List.of("custom", "year")) {
                var pdf = service.exportReportPdf(
                        station.id(),
                        List.of(),
                        List.of(everybody),
                        day.minus(1, ChronoUnit.HOURS),
                        day.plus(1, ChronoUnit.DAYS),
                        "exact",
                        "Test",
                        period);
                assertTrue(pdf.isPresent(), "the " + period + " printout renders in " + locale);
            }
        }
        stationRepo.updateLocale(station.id(), "de-DE");
    }

    @Test
    void theNoteBesideANameNamesOnlyWhatCounts() {
        assertEquals(
                "+30 Min. Vorbereitung, +15 Min. Nachbereitung", AttendanceReportService.extraTimeText(30, 15, "de"));
        assertEquals("+15 min follow-up", AttendanceReportService.extraTimeText(0, 15, "en"));
        assertEquals("", AttendanceReportService.extraTimeText(0, 0, "de"));
    }

    @Test
    void aTagCreditsWhileItIsOpenAndNobodyOnceItIsPrivate() {
        UserTag trainer = userTagRepo.create(station.id(), "Ausbilder Extra");
        userTagRepo.addMember(trainer.id(), leader.id());
        var byTag =
                new RestrictionAudience(List.of(), List.of(), List.of(trainer.id()), List.of(), RestrictionMode.AND);
        int sessionId = sheet(8, null, List.of(new TimeRuleInput("Ausbilder", 30, 0, byTag)));

        assertEquals(2.0, entryOf(report(8, "exact"), sessionId, leader).hours(), 0.001);

        userTagRepo.update(trainer.id(), trainer.name(), null, TagVisibility.PRIVATE, 0);
        var hidden = entryOf(report(8, "exact"), sessionId, leader);

        assertEquals(1.5, hidden.hours(), 0.001, "a private tag credits nobody");
        assertEquals(0, hidden.preparationMinutes());
    }
}
