/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The demo reads today and the hours of a day on the station's clock, never on the server's. */
class DemoClockTest {
    private static final Instant HALF_PAST_MIDNIGHT_IN_BERLIN = Instant.parse("2026-03-10T23:30:00Z");

    @Test
    void todayIsTheStationsToday() {
        var station = mock(Station.class);
        when(station.timezone()).thenReturn("Europe/Berlin");
        var clock = new DemoClock(Clock.fixed(HALF_PAST_MIDNIGHT_IN_BERLIN, ZoneOffset.UTC));

        var days = clock.of(station);

        assertEquals(LocalDate.of(2026, 3, 11), days.today());
        assertEquals(ZoneId.of("Europe/Berlin"), days.zone());
    }

    @Test
    void anHourIsTheHourOnTheStationsClock() {
        var days = new DemoStationDays(LocalDate.of(2026, 3, 11), ZoneId.of("Europe/Berlin"));

        assertEquals(Instant.parse("2026-03-11T15:00:00Z"), days.at(days.today(), 16, 0));
    }

    @Test
    void aStationWithoutAZoneIsReadInUtc() {
        var days = new DemoClock(Clock.fixed(HALF_PAST_MIDNIGHT_IN_BERLIN, ZoneOffset.UTC)).of(null);

        assertEquals(LocalDate.of(2026, 3, 10), days.today());
    }

    @Test
    void theInjectedClockIsTheSystemClock() {
        assertNotNull(new DemoClock().of(null).today());
    }
}
