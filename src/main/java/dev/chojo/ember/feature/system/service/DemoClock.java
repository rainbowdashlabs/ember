/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationFormat;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * The clock the demo dates what it seeds on, read on each station's own clock.
 *
 * <p>The server runs on UTC and a station mostly does not. Asking the server what day it is put
 * "today's" appointment of a station in Berlin on the day before for the two hours after midnight
 * there, and wrote its hours as UTC ones, so an evening drill started two hours late.
 */
@Singleton
public class DemoClock {
    private final Clock clock;

    @Inject
    public DemoClock() {
        this(Clock.systemUTC());
    }

    /**
     * @param clock the clock to read, fixed in tests to seed at a chosen moment
     */
    public DemoClock(Clock clock) {
        this.clock = clock;
    }

    /**
     * The days of one station, starting from its today.
     *
     * @param station the station the seeded data belongs to
     * @return today and the times of day as that station has them
     */
    public DemoStationDays of(Station station) {
        ZoneId zone = StationFormat.timezoneOf(station);
        return new DemoStationDays(LocalDate.now(clock.withZone(zone)), zone);
    }
}
