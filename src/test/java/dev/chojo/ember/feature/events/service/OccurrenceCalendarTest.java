/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which day an appointment is read for, and which days it may be answered for. */
class OccurrenceCalendarTest extends RepositoryTestBase {

    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Occurrence Calendar Station");
    }

    private StationEvent event(StationEvent.EventType type, Integer dayOfWeek, Instant start) {
        return eventRepo.create(
                station.id(),
                "Dated Event",
                "desc",
                type,
                dayOfWeek,
                start,
                start == null ? null : start.plus(2, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    void aOneOffIsReadForItsOwnDay() {
        Instant start = Instant.now().plus(5, ChronoUnit.DAYS);
        var one = event(StationEvent.EventType.ONE_TIME, null, start);
        LocalDate day = start.atZone(zone()).toLocalDate();

        assertEquals(day, occurrenceCalendar.dateInView(one).orElseThrow());
        assertEquals(day, occurrenceCalendar.dateInView(one.id()).orElseThrow());
        assertEquals(day, occurrenceCalendar.next(one).orElseThrow());
        assertEquals(List.of(day), occurrenceCalendar.occurrencesWithin(one, 365));
        assertEquals(day, occurrenceCalendar.dateToAnswerFor(one, null), "a one-off answers for its own day");
    }

    @Test
    void anAppointmentNobodyKnowsHasNoDay() {
        assertTrue(occurrenceCalendar.dateInView(987654).isEmpty());
        assertTrue(occurrenceCalendar.datesInView(List.of()).isEmpty());
    }

    @Test
    void aWeeklyAppointmentFallsOnItsOwnWeekday() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.WEDNESDAY.getValue(), start);

        LocalDate next = occurrenceCalendar.dateInView(weekly).orElseThrow();
        assertEquals(DayOfWeek.WEDNESDAY, next.getDayOfWeek());
        assertFalse(next.isBefore(LocalDate.now(zone())));

        var inTwoWeeks = occurrenceCalendar.occurrencesWithin(weekly, 13);
        assertEquals(2, inTwoWeeks.size());
        assertEquals(next, inTwoWeeks.getFirst());
        assertEquals(next, occurrenceCalendar.datesInView(List.of(weekly)).get(weekly.id()));
    }

    /**
     * A monthly appointment falls on the first of its weekday in the month, and a quarterly one every
     * third month counted from its own first date.
     */
    @Test
    void anAppointmentRepeatingLessOftenThanWeeklyIsNotAWeekAway() {
        LocalDate firstSaturday =
                YearMonth.now(zone()).minusMonths(2).atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
        Instant start = firstSaturday.atTime(10, 0).atZone(zone()).toInstant();
        var monthly = event(StationEvent.EventType.MONTHLY_FIRST, DayOfWeek.SATURDAY.getValue(), start);
        var quarterly = event(StationEvent.EventType.QUARTERLY, DayOfWeek.SATURDAY.getValue(), start);

        var monthlyYear = occurrenceCalendar.occurrencesWithin(monthly, 365);
        assertTrue(monthlyYear.size() >= 12, "one a month, all year");
        assertTrue(monthlyYear.stream()
                .allMatch(date -> date.getDayOfWeek() == DayOfWeek.SATURDAY && date.getDayOfMonth() <= 7));

        var quarterlyYear = occurrenceCalendar.occurrencesWithin(quarterly, 365);
        assertTrue(quarterlyYear.size() >= 4, "one a quarter, all year");
        assertTrue(quarterlyYear.stream()
                .allMatch(date ->
                        ChronoUnit.MONTHS.between(YearMonth.from(firstSaturday), YearMonth.from(date)) % 3 == 0));
    }

    /** A break the station keeps is not a date anything repeating falls on. */
    @Test
    void aBreakTakesItsDatesOut() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.THURSDAY.getValue(), start);
        LocalDate firstThursday = LocalDate.now(zone()).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        var closed = eventBreakRepo.create(station.id(), "Ferien", firstThursday, firstThursday);

        assertEquals(
                firstThursday.plusWeeks(1),
                occurrenceCalendar.dateInView(weekly).orElseThrow());
        assertFalse(occurrenceCalendar.occurrencesWithin(weekly, 21).contains(firstThursday));
        assertRefused(
                Refusal.REGISTRATION_DAY_IN_A_BREAK, () -> occurrenceCalendar.dateToAnswerFor(weekly, firstThursday));

        eventBreakRepo.delete(closed.id());
    }

    /** A series that has run out has no next date and is read for the last date it had. */
    @Test
    void aSeriesThatIsOverHasNoNextDateAndKeepsItsLastOne() {
        Instant start = Instant.now().minus(200, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.MONDAY.getValue(), start);
        LocalDate ended = LocalDate.now(zone()).minusDays(30);
        eventRepo.updateRepeatEnd(weekly.id(), ended, null);
        var over = eventRepo.findById(weekly.id()).orElseThrow();
        LocalDate lastMonday = ended.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        assertTrue(occurrenceCalendar.next(over).isEmpty());
        assertEquals(lastMonday, occurrenceCalendar.dateInView(over).orElseThrow());
        assertTrue(occurrenceCalendar.occurrencesWithin(over, 365).isEmpty());
        assertRefused(
                Refusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE,
                () -> occurrenceCalendar.dateToAnswerFor(over, lastMonday.plusWeeks(1)));
    }

    /**
     * Being on the right weekday is not enough to be answered for: a series on the first Monday of
     * the month takes no answer for the second one.
     */
    @Test
    void aFirstMondaySeriesTakesAnswersForTheFirstMondayOnly() {
        LocalDate firstMonday =
                YearMonth.now(zone()).plusMonths(1).atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        var monthly = event(
                StationEvent.EventType.MONTHLY_FIRST,
                DayOfWeek.MONDAY.getValue(),
                firstMonday.atTime(18, 0).atZone(zone()).toInstant());

        assertEquals(firstMonday, occurrenceCalendar.dateToAnswerFor(monthly, firstMonday));
        assertRefused(
                Refusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE,
                () -> occurrenceCalendar.dateToAnswerFor(monthly, firstMonday.plusWeeks(1)));
        assertRefused(Refusal.REGISTRATION_NEEDS_A_DAY, () -> occurrenceCalendar.dateToAnswerFor(monthly, null));
    }

    /** A series that ends after a number of times takes no answer for the time after that. */
    @Test
    void aCountedSeriesTakesNoAnswerPastItsLastTime() {
        LocalDate first = LocalDate.now(zone()).plusDays(1);
        var twice = event(
                StationEvent.EventType.RECURRING,
                first.getDayOfWeek().getValue(),
                first.atTime(18, 0).atZone(zone()).toInstant());
        eventRepo.updateRepeatEnd(twice.id(), null, 2);
        var counted = eventRepo.findById(twice.id()).orElseThrow();

        assertEquals(first.plusWeeks(1), occurrenceCalendar.dateToAnswerFor(counted, first.plusWeeks(1)));
        assertRefused(
                Refusal.REGISTRATION_DAY_NOT_AN_OCCURRENCE,
                () -> occurrenceCalendar.dateToAnswerFor(counted, first.plusWeeks(2)));
    }

    private static void assertRefused(Refusal refusal, Runnable call) {
        var thrown = assertThrows(RefusalResponse.class, call::run);
        assertEquals(refusal, thrown.refusal());
    }

    /** The station's own clock, which is the one every date here is read on. */
    private static ZoneId zone() {
        return occurrenceCalendar.zoneOf(station.id());
    }
}
