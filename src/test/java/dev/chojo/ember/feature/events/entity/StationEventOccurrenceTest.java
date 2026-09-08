/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When an appointment runs on a given day.
 *
 * <p>A repeating appointment keeps the date it was first configured on, and an attendance sheet that
 * took that date as it stood opened for an evening years past. What it really carries is a time of
 * day and a length, and this is where they are placed on the day being recorded.
 */
class StationEventOccurrenceTest {

    /** A Wednesday evening. */
    private static final Instant START = Instant.parse("2024-09-04T18:00:00Z");

    private static StationEvent event(StationEvent.EventType type, Instant start, Instant end) {
        return new StationEvent(
                1,
                1,
                "Übung",
                null,
                type,
                3,
                start,
                end,
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
                null);
    }

    @Test
    void aRepeatingAppointmentRunsOnTheDayItIsRecordedFor() {
        var weekly = event(StationEvent.EventType.RECURRING, START, START.plus(Duration.ofHours(2)));

        var span = weekly.occurrenceOn(LocalDate.parse("2026-09-02")).orElseThrow();

        assertEquals(Instant.parse("2026-09-02T18:00:00Z"), span.start());
        assertEquals(Instant.parse("2026-09-02T20:00:00Z"), span.end());
    }

    /** A repeating occasion that runs into the next day keeps its length rather than its dates. */
    @Test
    void aRepeatingAppointmentKeepsItsLengthPastMidnight() {
        var weekend = event(StationEvent.EventType.RECURRING, START, START.plus(Duration.ofHours(44)));

        var span = weekend.occurrenceOn(LocalDate.parse("2026-09-02")).orElseThrow();

        assertEquals(Instant.parse("2026-09-02T18:00:00Z"), span.start());
        assertEquals(Instant.parse("2026-09-04T14:00:00Z"), span.end());
    }

    /** A one-off appointment carries its own dates, which is what lets one run over a weekend. */
    @Test
    void aOneOffAppointmentKeepsTheDatesItWasGiven() {
        Instant friday = Instant.parse("2026-06-05T16:00:00Z");
        var camp = event(StationEvent.EventType.ONE_TIME, friday, friday.plus(Duration.ofHours(44)));

        var span = camp.occurrenceOn(LocalDate.parse("2026-09-02")).orElseThrow();

        assertEquals(friday, span.start());
        assertEquals(friday.plus(Duration.ofHours(44)), span.end());
    }

    @Test
    void anAppointmentWithoutAnEndRunsForNoTimeAtAll() {
        var weekly = event(StationEvent.EventType.RECURRING, START, null);

        var span = weekly.occurrenceOn(LocalDate.parse("2026-09-02")).orElseThrow();

        assertEquals(span.start(), span.end());
    }

    @Test
    void anAppointmentWithoutTimesHasNoOccurrence() {
        assertTrue(event(StationEvent.EventType.RECURRING, null, null)
                .occurrenceOn(LocalDate.parse("2026-09-02"))
                .isEmpty());
    }
}
