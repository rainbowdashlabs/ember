/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which day an appointment is read for, which every answer given per date hangs off. */
class EventDateResolverTest extends RepositoryTestBase {

    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Date Resolver Station");
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

        assertEquals(
                start.atZone(dateResolverZone()).toLocalDate(),
                eventDateResolver.nextDate(one).orElseThrow());
        assertEquals(
                eventDateResolver.nextDate(one).orElseThrow(),
                eventDateResolver.nextDate(one.id()).orElseThrow());
        assertEquals(1, eventDateResolver.occurrencesWithin(one, 365).size());
    }

    @Test
    void anAppointmentNobodyKnowsHasNoDay() {
        assertTrue(eventDateResolver.nextDate(987654).isEmpty());
        assertTrue(eventDateResolver.nextDates(List.of()).isEmpty());
    }

    @Test
    void aWeeklyAppointmentFallsOnItsOwnWeekday() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.WEDNESDAY.getValue(), start);

        LocalDate next = eventDateResolver.nextDate(weekly).orElseThrow();
        assertEquals(DayOfWeek.WEDNESDAY, next.getDayOfWeek());
        assertFalse(next.isBefore(LocalDate.now(dateResolverZone())));

        var inTwoWeeks = eventDateResolver.occurrencesWithin(weekly, 14);
        assertEquals(2, inTwoWeeks.size());
        assertEquals(next, inTwoWeeks.getFirst());
        assertTrue(inTwoWeeks.stream().allMatch(date -> date.getDayOfWeek() == DayOfWeek.WEDNESDAY));

        assertEquals(next, eventDateResolver.nextDates(List.of(weekly)).get(weekly.id()));
    }

    /** A break the station keeps is not a date anything falls on. */
    @Test
    void aBreakTakesItsDatesOut() {
        Instant start = Instant.now().minus(30, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.THURSDAY.getValue(), start);
        LocalDate firstThursday =
                LocalDate.now(dateResolverZone()).with(TemporalAdjusters.nextOrSame(DayOfWeek.THURSDAY));
        var closed = eventBreakRepo.create(station.id(), "Ferien", firstThursday, firstThursday);

        LocalDate next = eventDateResolver.nextDate(weekly).orElseThrow();
        assertEquals(firstThursday.plusWeeks(1), next);
        assertFalse(eventDateResolver.occurrencesWithin(weekly, 21).contains(firstThursday));

        eventBreakRepo.delete(closed.id());
    }

    /** A series that has run out keeps its last date, so a page about it still says which day it was. */
    @Test
    void aSeriesThatIsOverKeepsItsLastDate() {
        Instant start = Instant.now().minus(200, ChronoUnit.DAYS);
        var weekly = event(StationEvent.EventType.RECURRING, DayOfWeek.MONDAY.getValue(), start);
        LocalDate ended = LocalDate.now(dateResolverZone())
                .minusDays(30)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        eventRepo.updateRepeatEnd(weekly.id(), ended, null);
        var over = eventRepo.findById(weekly.id()).orElseThrow();

        assertEquals(ended, eventDateResolver.nextDate(over).orElseThrow());
        assertTrue(eventDateResolver.occurrencesWithin(over, 365).isEmpty());
    }

    /** The station's own clock, which is the one every date here is read on. */
    private static ZoneId dateResolverZone() {
        return eventDateResolver.zoneOf(station.id());
    }
}
