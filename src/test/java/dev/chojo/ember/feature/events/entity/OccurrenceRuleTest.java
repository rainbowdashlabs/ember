/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.events.entity.StationEvent.EventType;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import net.fortuna.ical4j.model.Recur;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which dates a repeating appointment falls on, and how that is written down for a calendar.
 *
 * <p>The application and the calendar feed read the same appointment, one through this rule and one
 * through the rule a calendar is handed. The last test holds the two to each other.
 */
class OccurrenceRuleTest {

    /** Wednesday. */
    private static final Instant START = Instant.parse("2026-09-02T18:00:00Z");

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static StationEvent event(
            EventType type, Integer dayOfWeek, Instant start, LocalDate until, Integer count) {
        return new StationEvent(
                1,
                1,
                "Übung",
                null,
                type,
                dayOfWeek,
                start,
                start == null ? null : start.plusSeconds(7200),
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
                null,
                until,
                count);
    }

    private static StationEvent weekly(LocalDate until, Integer count) {
        return event(EventType.RECURRING, 3, START, until, count);
    }

    private static OccurrenceRule rule(StationEvent event) {
        return OccurrenceRule.of(event, ZoneOffset.UTC).orElseThrow();
    }

    private static List<LocalDate> dates(OccurrenceRule rule, LocalDate from, LocalDate to) {
        var dates = new ArrayList<LocalDate>();
        var date = rule.onOrAfter(from);
        while (date.isPresent() && !date.get().isAfter(to)) {
            dates.add(date.get());
            date = rule.onOrAfter(date.get().plusDays(1));
        }
        return dates;
    }

    private static LocalDate date(String iso) {
        return LocalDate.parse(iso);
    }

    @Test
    void withoutAnEndItRepeatsForEver() {
        var rule = rule(weekly(null, null));

        assertTrue(rule.matches(date("2026-09-09")));
        assertTrue(rule.matches(date("2031-01-01").with(DayOfWeek.WEDNESDAY)));
        assertEquals(null, rule.last());
    }

    /** Nothing falls before the first date: a series has no past before it was set up. */
    @Test
    void nothingFallsBeforeTheFirstDate() {
        var rule = rule(weekly(null, null));

        assertFalse(rule.matches(date("2026-08-26")));
        assertTrue(rule.before(date("2026-09-02")).isEmpty());
        assertEquals(date("2026-09-02"), rule.onOrAfter(date("2020-01-01")).orElseThrow());
    }

    @Test
    void aLastDayEndsIt() {
        var rule = rule(weekly(date("2026-09-20"), null));

        assertEquals(date("2026-09-16"), rule.last(), "the last date the series falls on, not the day named");
        assertTrue(rule.matches(date("2026-09-16")), "the last day itself still counts");
        assertFalse(rule.matches(date("2026-09-23")));
        assertTrue(rule.onOrAfter(date("2026-09-17")).isEmpty());
        assertEquals(date("2026-09-16"), rule.before(date("2027-01-01")).orElseThrow());
    }

    /** Eight times means eight dates, counted from the first one rather than from today. */
    @Test
    void aNumberOfTimesEndsItOnTheDateOfTheLastOne() {
        var rule = rule(weekly(null, 3));

        assertEquals(date("2026-09-16"), rule.last());
        assertEquals(
                List.of(date("2026-09-02"), date("2026-09-09"), date("2026-09-16")),
                dates(rule, date("2026-01-01"), date("2027-01-01")));
    }

    /** The weekday can differ from the start date, and then the first date is the one after it. */
    @Test
    void countingStartsAtTheFirstMatchingWeekday() {
        var friday = rule(event(EventType.RECURRING, 5, START, null, 2));

        assertEquals(date("2026-09-04"), friday.first());
        assertEquals(date("2026-09-11"), friday.last());
    }

    /** A monthly series is the first of its weekday in the month, and no other of them. */
    @Test
    void aMonthlySeriesFallsOnTheFirstOfItsWeekday() {
        var firstMonday = rule(event(EventType.MONTHLY_FIRST, 1, Instant.parse("2026-01-05T17:00:00Z"), null, null));

        assertEquals(
                List.of(
                        date("2026-01-05"),
                        date("2026-02-02"),
                        date("2026-03-02"),
                        date("2026-04-06"),
                        date("2026-05-04"),
                        date("2026-06-01")),
                dates(firstMonday, date("2026-01-01"), date("2026-06-30")));
        assertFalse(firstMonday.matches(date("2026-01-12")), "the second Monday is not the first");
        assertEquals(date("2026-03-02"), firstMonday.before(date("2026-04-06")).orElseThrow());
    }

    @Test
    void aMonthlySeriesCountsMonths() {
        var monthly = rule(event(EventType.MONTHLY_FIRST, 3, START, null, 3));

        assertEquals(date("2026-11-04"), monthly.last(), "the first Wednesday");
        assertTrue(monthly.matches(date("2026-11-04")));
        assertFalse(monthly.matches(date("2026-12-02")));
    }

    /** A quarterly series comes round every third month from its own first date. */
    @Test
    void aQuarterlySeriesCountsFromItsOwnFirstDate() {
        var fromFebruary = rule(event(EventType.QUARTERLY, 1, Instant.parse("2026-02-02T17:00:00Z"), null, null));

        assertEquals(
                List.of(
                        date("2026-02-02"),
                        date("2026-05-04"),
                        date("2026-08-03"),
                        date("2026-11-02"),
                        date("2027-02-01")),
                dates(fromFebruary, date("2026-01-01"), date("2027-02-28")));
        assertFalse(fromFebruary.matches(date("2026-04-06")), "April opens a calendar quarter, not this one");
        assertEquals(date("2026-08-03"), fromFebruary.before(date("2026-11-02")).orElseThrow());
    }

    /** A start after the first weekday of its month waits for the next month the series falls in. */
    @Test
    void aQuarterlySeriesStartingLateInTheMonthBeginsThreeMonthsOn() {
        var late = rule(event(EventType.QUARTERLY, 1, Instant.parse("2026-02-20T17:00:00Z"), null, null));

        assertEquals(date("2026-05-04"), late.first());
    }

    @Test
    void aQuarterlySeriesCountsQuarters() {
        var quarterly = rule(event(EventType.QUARTERLY, 3, START, null, 2));

        assertEquals(date("2026-12-02"), quarterly.last());
    }

    /** A yearly series needs no weekday: it is the day and month of its first date. */
    @Test
    void aYearlySeriesWithoutAWeekdayRepeatsEveryYear() {
        var yearly = rule(event(EventType.YEARLY, null, Instant.parse("2026-05-01T08:00:00Z"), null, null));

        assertEquals(
                List.of(date("2026-05-01"), date("2027-05-01"), date("2028-05-01")),
                dates(yearly, date("2026-01-01"), date("2028-12-31")));
        assertEquals(date("2027-05-01"), yearly.before(date("2028-05-01")).orElseThrow());
    }

    /** The 29 February comes round in leap years only, as it does in a calendar. */
    @Test
    void aLeapDayComesRoundInLeapYearsOnly() {
        var leap = rule(event(EventType.YEARLY, null, Instant.parse("2028-02-29T10:00:00Z"), null, null));

        assertEquals(date("2032-02-29"), leap.onOrAfter(date("2028-03-01")).orElseThrow());
        assertEquals(date("2028-02-29"), leap.before(date("2032-02-29")).orElseThrow());
    }

    @Test
    void aYearlySeriesCountsYears() {
        var yearly = rule(event(EventType.YEARLY, 3, START, null, 2));

        assertEquals(date("2027-09-02"), yearly.last());
    }

    /**
     * The day of the year is read on the station's clock. Half an hour after midnight in Berlin is
     * still the day before in UTC, and the anniversary would be the wrong one.
     */
    @Test
    void aYearlySeriesBelongsToTheDayTheStationIsOn() {
        var justAfterMidnight = event(EventType.YEARLY, null, Instant.parse("2026-03-05T23:30:00Z"), null, null);
        var rule = OccurrenceRule.of(justAfterMidnight, BERLIN).orElseThrow();

        assertTrue(rule.matches(date("2027-03-06")));
        assertFalse(rule.matches(date("2027-03-05")));
    }

    @Test
    void aOneOffFallsOnItsOwnDayOnly() {
        var once = rule(event(EventType.ONE_TIME, null, START, date("2026-09-09"), null));

        assertEquals(date("2026-09-02"), once.first());
        assertEquals(date("2026-09-02"), once.last());
        assertTrue(once.onOrAfter(date("2026-09-03")).isEmpty());
        assertEquals(date("2026-09-02"), once.before(date("2026-09-03")).orElseThrow());
        assertTrue(once.calendarRule().isEmpty());
    }

    @Test
    void anAppointmentWithoutAStartOrWithAnEndBeforeItFallsOnNoDay() {
        assertTrue(OccurrenceRule.of(event(EventType.RECURRING, 3, null, null, null), BERLIN)
                .isEmpty());
        assertTrue(OccurrenceRule.of(weekly(date("2026-09-01"), null), ZoneOffset.UTC)
                .isEmpty());
    }

    /** A series that names no weekday takes the one of its start, which is what a calendar does. */
    @Test
    void aWeeklySeriesWithoutAWeekdayTakesTheOneOfItsStart() {
        var rule = rule(event(EventType.RECURRING, null, START, null, null));

        assertEquals(DayOfWeek.WEDNESDAY, rule.weekday());
        assertEquals("FREQ=WEEKLY;BYDAY=WE", rule.calendarRule().orElseThrow());
    }

    /** The calendar file says the end too, or a subscribed calendar repeats it for ever. */
    @Test
    void theRuleForACalendarCarriesTheEnd() {
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE", rule(weekly(null, null)).calendarRule().orElseThrow());
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE;UNTIL=20260916T235959Z",
                rule(weekly(date("2026-09-16"), null)).calendarRule().orElseThrow());
        assertEquals(
                "FREQ=WEEKLY;BYDAY=WE;UNTIL=20260916T235959Z",
                rule(weekly(null, 3)).calendarRule().orElseThrow(),
                "a number of times reaches the calendar as the day it runs out");
    }

    /**
     * The rule a calendar is handed names exactly the dates the application names, for every kind
     * of series, with and without an end. Expanded by the same library a calendar client would use.
     */
    @Test
    void theCalendarRuleNamesTheSameDatesAsTheApplication() {
        var series = List.of(
                event(EventType.RECURRING, 2, START, null, null),
                event(EventType.MONTHLY_FIRST, 1, START, null, null),
                event(EventType.QUARTERLY, 1, Instant.parse("2026-02-02T17:00:00Z"), null, null),
                event(EventType.QUARTERLY, 5, Instant.parse("2026-11-20T17:00:00Z"), null, 5),
                event(EventType.YEARLY, null, Instant.parse("2028-02-29T10:00:00Z"), null, null),
                event(EventType.MONTHLY_FIRST, 6, START, date("2027-06-30"), null));

        for (var event : series) {
            var rule = rule(event);
            LocalDate horizon = rule.first().plusYears(9);
            var seed = ZonedDateTime.of(rule.first(), LocalTime.of(17, 0), ZoneOffset.UTC);
            Recur<ZonedDateTime> recur = new Recur<>(rule.calendarRule().orElseThrow());
            var fromCalendar = recur.getDates(seed, seed, seed.plusYears(9)).stream()
                    .map(ZonedDateTime::toLocalDate)
                    .toList();

            assertEquals(fromCalendar, dates(rule, rule.first(), horizon), event.eventType() + " " + rule);
        }
    }
}
