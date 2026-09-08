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
    private static final Instant EVENING_START = Instant.parse("2026-09-02T16:00:00Z");
    private static final Instant EVENING_END = Instant.parse("2026-09-02T20:00:00Z");

    private static AttendanceSession sheet(Instant start, Instant end, Integer countedMinutes) {
        return new AttendanceSession(1, 1, start, end, start, null, "Sheet", null, null, countedMinutes);
    }

    @Test
    void withoutANumberOfItsOwnTheClockDecides() {
        var evening = sheet(EVENING_START, EVENING_END, null);

        assertEquals(4.0, evening.countedHours(EVENING_START, EVENING_END));
        assertEquals(2.0, evening.countedHours(EVENING_START, EVENING_START.plusSeconds(7200)));
    }

    @Test
    void aWholePresenceCountsTheNumberTheSheetCarries() {
        var evening = sheet(EVENING_START, EVENING_END, 180);

        assertEquals(3.0, evening.countedHours(EVENING_START, EVENING_END));
    }

    @Test
    void halfAnEveningCountsHalfOfIt() {
        var evening = sheet(EVENING_START, EVENING_END, 180);

        assertEquals(1.5, evening.countedHours(EVENING_START, EVENING_START.plusSeconds(7200)));
    }

    /** An arrival written before the sheet began does not buy hours nobody was there for. */
    @Test
    void nobodyCountsMoreThanTheSheetIsWorth() {
        var evening = sheet(EVENING_START, EVENING_END, 180);

        assertEquals(3.0, evening.countedHours(EVENING_START.minusSeconds(7200), EVENING_END.plusSeconds(7200)));
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
        var evening = sheet(EVENING_START, EVENING_END, 180);

        assertEquals(0.0, evening.countedHours(EVENING_END, EVENING_START));
        assertEquals(0.0, sheet(EVENING_START, EVENING_END, null).countedHours(EVENING_END, EVENING_START));
    }

    /** A sheet of no length still knows what a presence at it counts as. */
    @Test
    void aSheetWithoutASpanCountsTheWholeNumber() {
        var instant = sheet(EVENING_START, EVENING_START, 60);

        assertEquals(1.0, instant.countedHours(EVENING_START, EVENING_START.plusSeconds(60)));
    }

    @Test
    void aSheetKnowsWhetherItRunsIntoAnotherDay() {
        assertFalse(sheet(EVENING_START, EVENING_END, null).spansDays(BERLIN));
        assertTrue(sheet(EVENING_START, Instant.parse("2026-09-03T09:00:00Z"), null)
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
