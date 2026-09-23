/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a sheet says one member's presence is worth.
 *
 * <p>The report adds these numbers up and a station pays against them, so the two readings that must
 * not blur into one another are checked here: the clock, where the sheet says nothing else, and the
 * share of a number the sheet carries, where it does.
 */
class AttendanceSessionCountedHoursTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Instant SESSION_START = Instant.parse("2026-09-02T16:00:00Z");
    private static final Instant SESSION_END = Instant.parse("2026-09-02T20:00:00Z");

    private static AttendanceSession sheet(Instant start, Instant end, Integer countedMinutes) {
        return new AttendanceSession(1, 1, start, end, start, null, "Sheet", null, null, countedMinutes);
    }

    @Test
    void withoutANumberOfItsOwnTheClockDecides() {
        var session = sheet(SESSION_START, SESSION_END, null);

        assertEquals(4.0, session.countedHours(SESSION_START, SESSION_END));
        assertEquals(2.0, session.countedHours(SESSION_START, SESSION_START.plusSeconds(7200)));
    }

    /**
     * Setting up and clearing away happen either side of the appointment everybody else turns up
     * for, and the two hours it is scheduled for are not what whoever did them was there for. A
     * sheet that names no number of its own counts the clock it is given, past its own ends included.
     */
    @Test
    void aPresenceReachingPastTheSheetCountsWhatTheClockSays() {
        var session = sheet(Instant.parse("2026-09-02T17:00:00Z"), Instant.parse("2026-09-02T19:00:00Z"), null);

        assertEquals(
                4.0,
                session.countedHours(Instant.parse("2026-09-02T16:00:00Z"), Instant.parse("2026-09-02T20:00:00Z")));
    }

    /** The same appointment with a number of its own caps everybody at it, whoever stayed on. */
    @Test
    void aSheetWithANumberOfItsOwnStillCapsThePresenceReachingPastIt() {
        var session = sheet(Instant.parse("2026-09-02T17:00:00Z"), Instant.parse("2026-09-02T19:00:00Z"), 120);

        assertEquals(
                2.0,
                session.countedHours(Instant.parse("2026-09-02T16:00:00Z"), Instant.parse("2026-09-02T20:00:00Z")));
    }

    @Test
    void aWholePresenceCountsTheNumberTheSheetCarries() {
        var session = sheet(SESSION_START, SESSION_END, 180);

        assertEquals(3.0, session.countedHours(SESSION_START, SESSION_END));
    }

    @Test
    void halfAnAppointmentCountsHalfOfIt() {
        var session = sheet(SESSION_START, SESSION_END, 180);

        assertEquals(1.5, session.countedHours(SESSION_START, SESSION_START.plusSeconds(7200)));
    }

    /** An arrival written before the sheet began does not buy hours nobody was there for. */
    @Test
    void nobodyCountsMoreThanTheSheetIsWorth() {
        var session = sheet(SESSION_START, SESSION_END, 180);

        assertEquals(3.0, session.countedHours(SESSION_START.minusSeconds(7200), SESSION_END.plusSeconds(7200)));
    }

    @Test
    void aWeekendCountsWhatItIsWorthAndNotItsNights() {
        var camp = sheet(Instant.parse("2026-09-04T16:00:00Z"), Instant.parse("2026-09-06T14:00:00Z"), 960);

        assertEquals(
                16.0, camp.countedHours(Instant.parse("2026-09-04T16:00:00Z"), Instant.parse("2026-09-06T14:00:00Z")));
        assertEquals(
                8.0, camp.countedHours(Instant.parse("2026-09-05T15:00:00Z"), Instant.parse("2026-09-06T14:00:00Z")));
    }

    @Test
    void aPresenceThatEndsBeforeItBeganIsWorthNothing() {
        var session = sheet(SESSION_START, SESSION_END, 180);

        assertEquals(0.0, session.countedHours(SESSION_END, SESSION_START));
        assertEquals(0.0, sheet(SESSION_START, SESSION_END, null).countedHours(SESSION_END, SESSION_START));
    }

    /** A sheet of no length still knows what a presence at it counts as. */
    @Test
    void aSheetWithoutASpanCountsTheWholeNumber() {
        var instant = sheet(SESSION_START, SESSION_START, 60);

        assertEquals(1.0, instant.countedHours(SESSION_START, SESSION_START.plusSeconds(60)));
    }

    @Test
    void aSheetKnowsWhetherItRunsIntoAnotherDay() {
        assertFalse(sheet(SESSION_START, SESSION_END, null).spansDays(BERLIN));
        assertTrue(sheet(SESSION_START, Instant.parse("2026-09-03T09:00:00Z"), null)
                .spansDays(BERLIN));
    }

    /** The same two moments are one evening in Berlin and two days three hours further east. */
    @Test
    void theStationsOwnDayDecidesWhereADayEnds() {
        var lateEvening = sheet(Instant.parse("2026-09-02T19:00:00Z"), Instant.parse("2026-09-02T21:00:00Z"), null);

        assertFalse(lateEvening.spansDays(BERLIN));
        assertTrue(lateEvening.spansDays(ZoneId.of("Europe/Moscow")));
    }
}
