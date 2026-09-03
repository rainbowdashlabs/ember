/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventExportServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    /**
     * The day a yearly appointment repeats on is the day it is on where the station is, not where
     * the clock happens to start. An appointment in the small hours of the sixth is on the sixth,
     * although the same moment is still the fifth in UTC.
     */
    @Test
    void aYearlyAppointmentBelongsToTheDayTheStationIsOn() {
        Instant justAfterMidnightInBerlin = Instant.parse("2026-03-05T23:30:00Z");

        assertTrue(EventExportService.fallsOnYearlyAnchor(justAfterMidnightInBerlin, BERLIN, LocalDate.of(2027, 3, 6)));
        assertFalse(
                EventExportService.fallsOnYearlyAnchor(justAfterMidnightInBerlin, BERLIN, LocalDate.of(2027, 3, 5)));
    }

    /**
     * The year is not part of it: that is what makes it yearly.
     */
    @Test
    void theYearIsNotPartOfTheAnchor() {
        Instant midMorning = Instant.parse("2020-07-14T09:00:00Z");

        assertTrue(EventExportService.fallsOnYearlyAnchor(midMorning, BERLIN, LocalDate.of(2026, 7, 14)));
        assertTrue(EventExportService.fallsOnYearlyAnchor(midMorning, BERLIN, LocalDate.of(2031, 7, 14)));
        assertFalse(EventExportService.fallsOnYearlyAnchor(midMorning, BERLIN, LocalDate.of(2026, 7, 15)));
    }

    /**
     * An appointment with no start has no day of the year, and says so rather than throwing.
     */
    @Test
    void anAppointmentWithoutAStartFallsOnNoDay() {
        assertFalse(EventExportService.fallsOnYearlyAnchor(null, BERLIN, LocalDate.of(2026, 1, 1)));
    }
}
