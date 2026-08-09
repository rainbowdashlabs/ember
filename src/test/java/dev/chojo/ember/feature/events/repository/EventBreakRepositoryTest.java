/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventBreakRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Break Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void createReadUpdateDelete() {
        var created = eventBreakRepo.create(
                station.id(), "Summer Break", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));
        assertEquals("Summer Break", created.name());
        assertEquals(LocalDate.of(2026, 7, 1), created.startDate());

        assertTrue(eventBreakRepo.findById(created.id()).isPresent());
        assertEquals(
                1,
                eventBreakRepo.findByStation(station.id()).stream()
                        .filter(b -> b.id() == created.id())
                        .count());

        assertTrue(eventBreakRepo.update(
                created.id(), "Winter Break", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5)));
        assertEquals(
                "Winter Break",
                eventBreakRepo.findById(created.id()).orElseThrow().name());

        assertTrue(eventBreakRepo.isDateInBreak(station.id(), LocalDate.of(2026, 12, 25)));
        assertFalse(eventBreakRepo.isDateInBreak(station.id(), LocalDate.of(2026, 6, 1)));

        assertTrue(eventBreakRepo.delete(created.id()));
        assertTrue(eventBreakRepo.findById(created.id()).isEmpty());
    }

    @Test
    void missingBreakIsReportedAsAbsent() {
        assertTrue(eventBreakRepo.findById(99999).isEmpty());
        assertFalse(eventBreakRepo.update(
                99999, "X", LocalDate.now(), LocalDate.now().plusDays(1)));
        assertFalse(eventBreakRepo.delete(99999));
    }

    @Test
    void isDateInBreakWithoutAnyBreaks() {
        assertFalse(eventBreakRepo.isDateInBreak(99999, LocalDate.now()));
    }
}
