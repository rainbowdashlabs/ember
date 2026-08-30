/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a repeating appointment stops repeating.
 *
 * <p>A series that has run its course has to be over everywhere the recurrence is asked about, which
 * is the calendar, the reminders and the registration deadlines alike. They all ask the appointment,
 * so the answer is checked here rather than once per caller.
 */
class StationEventRepeatEndTest {

    /** Wednesday. */
    private static final Instant START = Instant.parse("2026-09-02T18:00:00Z");

    private static StationEvent weekly(LocalDate until, Integer count) {
        return event(StationEvent.EventType.RECURRING, 3, until, count);
    }

    private static StationEvent event(StationEvent.EventType type, Integer dayOfWeek, LocalDate until, Integer count) {
        return new StationEvent(
                1,
                1,
                "Übung",
                null,
                type,
                dayOfWeek,
                START,
                START.plusSeconds(7200),
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
                until,
                count);
    }

    @Test
    void withoutAnEndItRepeatsForEver() {
        var event = weekly(null, null);

        assertTrue(event.occursOn(LocalDate.parse("2026-09-09")));
        assertTrue(event.occursOn(LocalDate.parse("2031-01-01").with(java.time.DayOfWeek.WEDNESDAY)));
        assertTrue(event.lastDate().isEmpty());
    }

    @Test
    void aLastDayEndsIt() {
        var event = weekly(LocalDate.parse("2026-09-16"), null);

        assertTrue(event.occursOn(LocalDate.parse("2026-09-16")), "the last day itself still counts");
        assertFalse(event.occursOn(LocalDate.parse("2026-09-23")));
    }

    /** Eight times means eight dates, counted from the first one rather than from today. */
    @Test
    void aNumberOfTimesEndsItOnTheDateOfTheLastOne() {
        var event = weekly(null, 3);

        assertEquals(LocalDate.parse("2026-09-16"), event.lastDate().orElseThrow());
        assertTrue(event.occursOn(LocalDate.parse("2026-09-02")));
        assertTrue(event.occursOn(LocalDate.parse("2026-09-16")));
        assertFalse(event.occursOn(LocalDate.parse("2026-09-23")));
    }

    /** The weekday can differ from the start date, and then the first date is the one after it. */
    @Test
    void countingStartsAtTheFirstMatchingWeekday() {
        var friday = event(StationEvent.EventType.RECURRING, 5, null, 2);

        assertEquals(LocalDate.parse("2026-09-11"), friday.lastDate().orElseThrow());
    }

    @Test
    void aMonthlyEventCountsMonths() {
        var monthly = event(StationEvent.EventType.MONTHLY_FIRST, 3, null, 3);

        assertEquals(LocalDate.parse("2026-11-04"), monthly.lastDate().orElseThrow(), "the first Wednesday");
        assertTrue(monthly.occursOn(LocalDate.parse("2026-11-04")));
        assertFalse(monthly.occursOn(LocalDate.parse("2026-12-02")));
    }

    @Test
    void aQuarterlyEventCountsQuarters() {
        var quarterly = event(StationEvent.EventType.QUARTERLY, 3, null, 2);

        assertEquals(LocalDate.parse("2026-12-02"), quarterly.lastDate().orElseThrow());
    }

    @Test
    void aYearlyEventCountsYears() {
        var yearly = event(StationEvent.EventType.YEARLY, 3, null, 2);

        assertEquals(LocalDate.parse("2027-09-02"), yearly.lastDate().orElseThrow());
    }

    @Test
    void aOneOffAppointmentHasNothingToEnd() {
        var once = event(StationEvent.EventType.ONE_TIME, 3, LocalDate.parse("2026-09-09"), null);

        assertTrue(once.lastDate().isEmpty());
    }

    /** The calendar file says the end too, or a subscribed calendar repeats it for ever. */
    @Test
    void theRuleForACalendarCarriesTheEnd() {
        assertEquals("FREQ=WEEKLY;BYDAY=WE", EventRecurrence.rule(weekly(null, null)));
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE;UNTIL=20260916T235959Z",
                EventRecurrence.rule(weekly(LocalDate.parse("2026-09-16"), null)));
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE;UNTIL=20260916T235959Z",
                EventRecurrence.rule(weekly(null, 3)),
                "a number of times reaches the calendar as the day it runs out");
    }
}
