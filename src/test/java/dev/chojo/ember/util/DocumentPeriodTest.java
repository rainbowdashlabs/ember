/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the stretch of time a document covers is said out loud.
 *
 * <p>This is what tells two exports of the same report apart in a folder, so it has to be the way a
 * reader would name the period themselves, in their own language.
 */
class DocumentPeriodTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void aMonthIsItsName() {
        assertEquals("Januar 2026", DocumentPeriod.of("month", atBerlin("2026-01-15T10:00:00"), BERLIN, "de"));
        assertEquals("January 2026", DocumentPeriod.of("month", atBerlin("2026-01-15T10:00:00"), BERLIN, "en"));
    }

    @Test
    void aWeekIsTheNumberPeopleScheduleBy() {
        assertEquals("KW 50 2026", DocumentPeriod.of("week", atBerlin("2026-12-10T10:00:00"), BERLIN, "de"));
        assertEquals("Week 50 2026", DocumentPeriod.of("week", atBerlin("2026-12-10T10:00:00"), BERLIN, "en"));
    }

    @Test
    void aQuarterIsTheQuarterInBothLanguages() {
        assertEquals("Q1 2026", DocumentPeriod.of("quarter", atBerlin("2026-02-03T10:00:00"), BERLIN, "de"));
        assertEquals("Q4 2026", DocumentPeriod.of("quarter", atBerlin("2026-11-03T10:00:00"), BERLIN, "en"));
    }

    @Test
    void aYearIsJustTheYear() {
        assertEquals("2026", DocumentPeriod.of("year", atBerlin("2026-06-01T10:00:00"), BERLIN, "de"));
    }

    @Test
    void anUnknownPeriodReadsAsAMonth() {
        assertEquals("Januar 2026", DocumentPeriod.of("weekly", atBerlin("2026-01-15T10:00:00"), BERLIN, "de"));
        assertEquals("Januar 2026", DocumentPeriod.of(null, atBerlin("2026-01-15T10:00:00"), BERLIN, "de"));
    }

    /**
     * A window opens at midnight where the station is, which is the evening before in UTC. Read in
     * the wrong zone, an export covering January would be named after December.
     */
    @Test
    void theStationsOwnZoneDecidesWhichMonthItIs() {
        Instant midnightInBerlin = Instant.parse("2025-12-31T23:00:00Z");

        assertEquals("Januar 2026", DocumentPeriod.of("month", midnightInBerlin, BERLIN, "de"));
        assertEquals("Dezember 2025", DocumentPeriod.of("month", midnightInBerlin, ZoneId.of("UTC"), "de"));
    }

    private static Instant atBerlin(String local) {
        return java.time.LocalDateTime.parse(local).atZone(BERLIN).toInstant();
    }
}
