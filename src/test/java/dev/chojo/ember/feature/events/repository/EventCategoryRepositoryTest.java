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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventCategoryRepositoryTest extends RepositoryTestBase {
    private static Station station;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Category Station");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    void createReadUpdateDelete() {
        var category = eventCategoryRepo.create(station.id(), "Training", 1, "#ff6421");
        assertEquals("Training", category.name());
        assertEquals(1, category.position());
        assertEquals("#ff6421", category.color());

        assertTrue(eventCategoryRepo.findById(category.id()).isPresent());
        assertTrue(eventCategoryRepo.findByStation(station.id()).stream().anyMatch(c -> c.id() == category.id()));

        assertTrue(eventCategoryRepo.update(category.id(), "Exercise", 2, 5, true, "#3694ff"));
        var updated = eventCategoryRepo.findById(category.id()).orElseThrow();
        assertEquals("Exercise", updated.name());
        assertEquals(2, updated.position());
        assertEquals("#3694ff", updated.color());
        assertTrue(updated.isPublic());

        assertTrue(eventCategoryRepo.delete(category.id()));
        assertTrue(eventCategoryRepo.findById(category.id()).isEmpty());
    }

    @Test
    void missingCategoryIsReportedAsAbsent() {
        assertTrue(eventCategoryRepo.findById(99999).isEmpty());
        assertFalse(eventCategoryRepo.update(99999, "X", 0, null, false, null));
        assertFalse(eventCategoryRepo.delete(99999));
    }

    @Test
    void reorderAssignsZeroBasedPositions() {
        var first = eventCategoryRepo.create(station.id(), "Reorder A", 0, null);
        var second = eventCategoryRepo.create(station.id(), "Reorder B", 1, null);
        var third = eventCategoryRepo.create(station.id(), "Reorder C", 2, null);
        try {
            eventCategoryRepo.reorder(station.id(), List.of(third.id(), first.id(), second.id()));
            assertEquals(0, eventCategoryRepo.findById(third.id()).orElseThrow().position());
            assertEquals(1, eventCategoryRepo.findById(first.id()).orElseThrow().position());
            assertEquals(
                    2, eventCategoryRepo.findById(second.id()).orElseThrow().position());
        } finally {
            eventCategoryRepo.delete(first.id());
            eventCategoryRepo.delete(second.id());
            eventCategoryRepo.delete(third.id());
        }
    }

    @Test
    void reorderIgnoresCategoriesOfOtherStations() {
        var otherStation = stationRepo.create("Other Category Station");
        var foreign = eventCategoryRepo.create(otherStation.id(), "Foreign", 7, null);
        try {
            eventCategoryRepo.reorder(station.id(), List.of(foreign.id()));
            assertEquals(
                    7, eventCategoryRepo.findById(foreign.id()).orElseThrow().position());
        } finally {
            eventCategoryRepo.delete(foreign.id());
            stationRepo.delete(otherStation.id());
        }
    }

    @Test
    void reorderWithoutIdsDoesNothing() {
        var category = eventCategoryRepo.create(station.id(), "Untouched", 3, null);
        try {
            eventCategoryRepo.reorder(station.id(), List.of());
            assertEquals(
                    3, eventCategoryRepo.findById(category.id()).orElseThrow().position());
        } finally {
            eventCategoryRepo.delete(category.id());
        }
    }
}
