/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.equipment.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquipmentOccurrenceWindowsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 6);

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static StationEvent event(Instant start, Instant end, StationEvent.EventType type) {
        return new StationEvent(
                1,
                1,
                "Test",
                "",
                type,
                type == StationEvent.EventType.ONE_TIME
                        ? null
                        : DAY.getDayOfWeek().getValue(),
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
                null,
                null,
                null);
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC);
    }

    @Test
    void anOccurrenceTakesTheClockTimeOfTheSeries() {
        var recurring = event(at(DAY, 19, 30), at(DAY, 21, 0), StationEvent.EventType.RECURRING);
        LocalDate later = DAY.plusWeeks(3);
        assertEquals(at(later, 19, 30), EquipmentOccurrenceWindows.startOf(recurring, later, ZoneOffset.UTC));
        assertEquals(at(later, 21, 0), EquipmentOccurrenceWindows.endOf(recurring, later, ZoneOffset.UTC));
    }

    /**
     * A series configured in summer time claims its gear at the same time of day in winter time. Read
     * in UTC, its evening started an hour early.
     */
    @Test
    void anOccurrenceKeepsItsClockTimeAcrossAChangeOfTheClocks() {
        var summerEvening = event(
                Instant.parse("2026-06-06T17:30:00Z"),
                Instant.parse("2026-06-06T19:00:00Z"),
                StationEvent.EventType.RECURRING);
        LocalDate winter = LocalDate.of(2026, 12, 5);
        assertEquals(
                Instant.parse("2026-12-05T18:30:00Z"),
                EquipmentOccurrenceWindows.startOf(summerEvening, winter, BERLIN));
        assertEquals(
                Instant.parse("2026-12-05T20:00:00Z"), EquipmentOccurrenceWindows.endOf(summerEvening, winter, BERLIN));
    }

    @Test
    void anOccurrenceRunningPastMidnightEndsTheFollowingDay() {
        var late = event(at(DAY, 22, 0), at(DAY, 2, 0), StationEvent.EventType.RECURRING);
        assertEquals(at(DAY.plusDays(1), 2, 0), EquipmentOccurrenceWindows.endOf(late, DAY, ZoneOffset.UTC));
    }

    @Test
    void anAppointmentWithoutTimesFallsBackToTheStartOfTheDay() {
        var vague = event(null, null, StationEvent.EventType.RECURRING);
        assertEquals(at(DAY, 0, 0), EquipmentOccurrenceWindows.startOf(vague, DAY, ZoneOffset.UTC));
        assertEquals(at(DAY, 0, 0), EquipmentOccurrenceWindows.endOf(vague, DAY, ZoneOffset.UTC));
        assertNull(EquipmentOccurrenceWindows.singleDateOf(vague, ZoneOffset.UTC));
    }

    @Test
    void anAppointmentThatHappensOnceKnowsItsDay() {
        var once = event(at(DAY, 9, 0), at(DAY, 17, 0), StationEvent.EventType.ONE_TIME);
        assertEquals(DAY, EquipmentOccurrenceWindows.singleDateOf(once, ZoneOffset.UTC));
    }

    /** Half past midnight in Berlin is the evening before in UTC, and the day is the station's. */
    @Test
    void anAppointmentThatHappensOnceFallsOnTheStationsDay() {
        var justAfterMidnight = event(
                Instant.parse("2026-06-05T22:30:00Z"),
                Instant.parse("2026-06-06T01:00:00Z"),
                StationEvent.EventType.ONE_TIME);
        assertEquals(DAY, EquipmentOccurrenceWindows.singleDateOf(justAfterMidnight, BERLIN));
    }
}
