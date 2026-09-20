/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What an attendance report is called once it reaches a reader.
 *
 * <p>The old name was the same for every export, so a second one landed beside the first as a copy
 * with a number after it and neither said what it held. A name now says what it is, whose it is and
 * when it covers.
 */
class AttendanceReportFileNameTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void aMonthlyReportIsNamedAfterItsMonth() {
        assertEquals(
                "Anwesenheit - Januar 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "de", ""));
    }

    @Test
    void aQuarterlyReportIsNamedAfterItsQuarter() {
        assertEquals(
                "Anwesenheit - Q1 2026.pdf",
                AttendanceReportService.reportFileName("quarter", at("2026-02-15T10:00"), BERLIN, "de", ""));
    }

    @Test
    void aYearlyReportKeepsTheSeparatorBeforeTheYear() {
        assertEquals(
                "Anwesenheit - 2026.pdf",
                AttendanceReportService.reportFileName("year", at("2026-06-15T10:00"), BERLIN, "de", ""));
    }

    @Test
    void aReportAboutOneGroupSaysWhichGroup() {
        assertEquals(
                "Anwesenheit - Jugend - Januar 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "de", "Jugend"));
    }

    /** A report over several groups is about all of them, so naming the first would mislead. */
    @Test
    void aReportAboutSeveralGroupsNamesNone() {
        assertEquals(
                "Anwesenheit - Januar 2026.pdf",
                AttendanceReportService.reportFileName(
                        "month", at("2026-01-15T10:00"), BERLIN, "de", "Jugend, Aktive"));
    }

    @Test
    void anEnglishStationGetsAnEnglishName() {
        assertEquals(
                "Attendance - January 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "en", ""));
    }

    /**
     * A kind of member is stored as a token and read as a word.
     *
     * <p>The first report that ever reached a downloads folder was called
     * {@code Anwesenheit - MEMBER - Januar 2026.pdf}, which is the database's word for it and nobody
     * else's.
     */
    @Test
    void aKindOfMemberIsNamedTheWayTheInterfaceNamesIt() {
        assertEquals(
                "Anwesenheit - Mitglied - Januar 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "de", "MEMBER"));
        assertEquals(
                "Attendance - Guardian - January 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "en", "GUARDIAN"));
    }

    /** A group called something a filesystem refuses still has to produce a name. */
    @Test
    void aGroupNamedAwkwardlyStillMakesAName() {
        assertEquals(
                "Anwesenheit - Jugend A B - Januar 2026.pdf",
                AttendanceReportService.reportFileName("month", at("2026-01-15T10:00"), BERLIN, "de", "Jugend A/B"));
    }

    private static Instant at(String local) {
        return LocalDateTime.parse(local).atZone(BERLIN).toInstant();
    }
}
