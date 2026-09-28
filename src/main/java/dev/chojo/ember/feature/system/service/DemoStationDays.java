/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Today and the hours of a day as one station has them, which is what everything the demo dates is
 * placed on.
 *
 * @param today the station's today when the seed ran
 * @param zone  the station's zone, which the hours of a day are read in
 */
public record DemoStationDays(LocalDate today, ZoneId zone) {

    /**
     * A time of day on a date, as the station's clock shows it.
     *
     * @param date   the day
     * @param hour   the hour on the station's clock
     * @param minute the minute
     * @return the moment that is
     */
    public Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(zone).toInstant();
    }
}
