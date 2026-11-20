/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.entity.UpcomingEventOccurrence;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The dashboard's mix of the upcoming list: one-time appointments however far away, repeating dates
 * only within a horizon, soonest first and up to a limit.
 */
class UpcomingSeriesWindowTest extends RepositoryTestBase {
    private static Station station;
    private static EventOccurrenceService occurrences;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Upcoming Series Window Station");
        occurrences = newEventServices(new DomainEventBus(Set.of())).occurrence();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    private static ZoneId zone() {
        return occurrenceCalendar.zoneOf(station.id());
    }

    private static StationEvent appointment(String name, StationEvent.EventType type, LocalDate first) {
        var start = first.atTime(18, 0).atZone(zone()).toInstant();
        return eventRepo.create(
                station.id(),
                name,
                "desc",
                type,
                type == StationEvent.EventType.ONE_TIME
                        ? null
                        : first.getDayOfWeek().getValue(),
                start,
                start.plus(Duration.ofHours(2)),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
    }

    private static List<UpcomingEventOccurrence> upcoming(int limit, LocalDate seriesTo) {
        return occurrences.findUpcomingOccurrences(
                station.id(),
                null,
                new EventOccurrenceService.OccurrenceQuery(
                        null, null, false, null, null, null, limit, 0, false, seriesTo));
    }

    @Test
    void aFarOneTimeAppointmentAndTheSeriesWithinTheHorizonAreListed() {
        var today = LocalDate.now(zone());
        var far = appointment("Fest", StationEvent.EventType.ONE_TIME, today.plusDays(40));
        var weekly = appointment("Dienst", StationEvent.EventType.RECURRING, today.plusDays(1));
        try {
            var listed = upcoming(10, today.plusDays(14));

            assertEquals(
                    List.of(today.plusDays(1), today.plusDays(8), today.plusDays(40)),
                    listed.stream().map(UpcomingEventOccurrence::date).toList(),
                    "two weekly dates within fourteen days, then the one-time appointment forty days out");
            assertEquals(
                    List.of(weekly.id(), weekly.id(), far.id()),
                    listed.stream().map(occurrence -> occurrence.event().id()).toList());
            assertEquals(
                    List.of(today.plusDays(1), today.plusDays(8)),
                    upcoming(2, today.plusDays(14)).stream()
                            .map(UpcomingEventOccurrence::date)
                            .toList(),
                    "the limit holds, soonest first");
        } finally {
            eventRepo.delete(far.id());
            eventRepo.delete(weekly.id());
        }
    }

    @Test
    void withoutTheHorizonOneWindowAppliesToBothKinds() {
        var today = LocalDate.now(zone());
        var far = appointment("Fest", StationEvent.EventType.ONE_TIME, today.plusDays(40));
        var weekly = appointment("Dienst", StationEvent.EventType.RECURRING, today.plusDays(1));
        try {
            var listed = upcoming(10, null);

            assertEquals(10, listed.size(), "the series fills the page as before");
            assertEquals(
                    today.plusDays(1).plusWeeks(8),
                    listed.getLast().date(),
                    "series dates past fourteen days are listed, the one-time appointment among them");
        } finally {
            eventRepo.delete(far.id());
            eventRepo.delete(weekly.id());
        }
    }
}
