/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.events.entity.StationCalendar.DateCheck;
import dev.chojo.ember.feature.events.entity.StationEvent.EventType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dates of a station's appointments once its breaks are taken out. */
class StationCalendarTest {

    /** Wednesday. */
    private static final Instant START = Instant.parse("2026-09-02T18:00:00Z");

    private static final EventBreak AUTUMN =
            new EventBreak(1, 1, "Herbstferien", LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-25"));

    private static final StationCalendar CALENDAR = new StationCalendar(ZoneOffset.UTC, List.of(AUTUMN));

    private static StationEvent event(EventType type, Integer dayOfWeek, Integer count) {
        return new StationEvent(
                1,
                1,
                "Übung",
                null,
                type,
                dayOfWeek,
                type == EventType.ONE_TIME ? Instant.parse("2026-10-14T18:00:00Z") : START,
                null,
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null,
                null,
                count);
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void aBreakTakesTheDatesOfASeriesOut() {
        var weekly = event(EventType.RECURRING, 3, null);

        assertEquals(DateCheck.IN_A_BREAK, CALENDAR.check(weekly, date("2026-10-14")));
        assertEquals(DateCheck.NOT_AN_OCCURRENCE, CALENDAR.check(weekly, date("2026-10-15")));
        assertEquals(DateCheck.OCCURRENCE, CALENDAR.check(weekly, date("2026-10-07")));
        assertFalse(CALENDAR.occursOn(weekly, date("2026-10-21")));
        assertEquals(
                List.of(date("2026-10-07"), date("2026-10-28")),
                CALENDAR.between(weekly, date("2026-10-05"), date("2026-10-31")));
    }

    @Test
    void theNextAndThePreviousDateStepOverABreak() {
        var weekly = event(EventType.RECURRING, 3, null);

        assertEquals(
                date("2026-10-28"), CALENDAR.next(weekly, date("2026-10-08")).orElseThrow());
        assertEquals(
                date("2026-10-07"),
                CALENDAR.previous(weekly, date("2026-10-28")).orElseThrow());
    }

    /** A break suspends a series and leaves a single date somebody put into it standing. */
    @Test
    void aOneOffInsideABreakStillTakesPlace() {
        var once = event(EventType.ONE_TIME, null, null);

        assertEquals(DateCheck.OCCURRENCE, CALENDAR.check(once, date("2026-10-14")));
        assertTrue(CALENDAR.suspendedDates(once).isEmpty());
    }

    /** The dates a break takes out still count towards a number of times, as they do in a calendar. */
    @Test
    void aCountedSeriesEndsWhereItsCountEndsBreaksIncluded() {
        var eightTimes = event(EventType.RECURRING, 3, 8);

        assertEquals(List.of(date("2026-10-14"), date("2026-10-21")), CALENDAR.suspendedDates(eightTimes));
        assertEquals(
                6,
                CALENDAR.between(eightTimes, date("2026-01-01"), date("2027-12-31"))
                        .size());
        assertTrue(CALENDAR.next(eightTimes, date("2026-10-22")).isEmpty());
    }

    /** A series that has run out is read for the last date it had. */
    @Test
    void theDateInViewOfASeriesThatIsOverIsItsLastOne() {
        var twice = event(EventType.RECURRING, 3, 2);

        assertTrue(CALENDAR.next(twice, CALENDAR.today()).isEmpty());
        assertEquals(date("2026-09-09"), CALENDAR.dateInView(twice).orElseThrow());
    }
}
