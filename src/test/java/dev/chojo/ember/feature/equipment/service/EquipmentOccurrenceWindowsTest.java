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
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EquipmentOccurrenceWindowsTest {

    private static final LocalDate DAY = LocalDate.of(2026, 6, 6);

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
                false,
                null,
                null,
                null);
    }

    private static Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).toInstant(ZoneOffset.UTC);
    }

    @Test
    void anEveningTakesTheClockTimeOfTheSeries() {
        var recurring = event(at(DAY, 19, 30), at(DAY, 21, 0), StationEvent.EventType.RECURRING);
        LocalDate later = DAY.plusWeeks(3);
        assertEquals(at(later, 19, 30), EquipmentOccurrenceWindows.startOf(recurring, later));
        assertEquals(at(later, 21, 0), EquipmentOccurrenceWindows.endOf(recurring, later));
    }

    @Test
    void anEveningRunningPastMidnightEndsTheFollowingDay() {
        var late = event(at(DAY, 22, 0), at(DAY, 2, 0), StationEvent.EventType.RECURRING);
        assertEquals(at(DAY.plusDays(1), 2, 0), EquipmentOccurrenceWindows.endOf(late, DAY));
    }

    @Test
    void anAppointmentWithoutTimesFallsBackToTheStartOfTheDay() {
        var vague = event(null, null, StationEvent.EventType.RECURRING);
        assertEquals(at(DAY, 0, 0), EquipmentOccurrenceWindows.startOf(vague, DAY));
        assertEquals(at(DAY, 0, 0), EquipmentOccurrenceWindows.endOf(vague, DAY));
        assertNull(EquipmentOccurrenceWindows.singleDateOf(vague));
    }

    @Test
    void anAppointmentThatHappensOnceKnowsItsDay() {
        var once = event(at(DAY, 9, 0), at(DAY, 17, 0), StationEvent.EventType.ONE_TIME);
        assertEquals(DAY, EquipmentOccurrenceWindows.singleDateOf(once));
    }
}
