/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.LayoutFieldEntry;
import dev.chojo.ember.feature.events.repository.EventLayoutRepository;
import dev.chojo.ember.feature.events.service.EventLayoutService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventLayoutServiceTest extends RepositoryTestBase {

    private static EventLayoutService service;
    private static Station station;
    private static int layoutId;

    @BeforeAll
    static void setup() {
        service = new EventLayoutService(new EventLayoutRepository());
        station = stationRepo.create("EventLayoutServiceStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createLayout() {
        var layout = service.create(station.id(), "Standard Layout");
        assertNotNull(layout);
        assertEquals("Standard Layout", layout.name());
        assertEquals(station.id(), layout.stationId());
        layoutId = layout.id();
    }

    @Test
    @Order(2)
    void findById() {
        var found = service.findById(layoutId);
        assertTrue(found.isPresent());
        assertEquals(layoutId, found.get().id());
        assertEquals("Standard Layout", found.get().name());
    }

    @Test
    @Order(3)
    void findByIdMissing() {
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(4)
    void findByStation() {
        var layouts = service.findByStation(station.id());
        assertTrue(layouts.stream().anyMatch(l -> l.id() == layoutId));
    }

    @Test
    @Order(5)
    void updateLayout() {
        boolean updated = service.update(layoutId, "Updated Layout");
        assertTrue(updated);
        var found = service.findById(layoutId).orElseThrow();
        assertEquals("Updated Layout", found.name());
    }

    @Test
    @Order(10)
    void replaceAndFindFields() {
        var fields = List.of(
                new LayoutFieldEntry("Location", EventFieldType.STRING, "{}", true, null),
                new LayoutFieldEntry("Notes", EventFieldType.STRING, "{}", false, null));
        service.replaceLayoutFields(layoutId, fields);

        var found = service.findFieldsByLayout(layoutId);
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Location") && f.overview()));
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Notes") && !f.overview()));
    }

    @Test
    @Order(11)
    void replaceLayoutFieldsClearsOld() {
        service.replaceLayoutFields(
                layoutId, List.of(new LayoutFieldEntry("SingleField", EventFieldType.STRING, "{}", false, null)));

        var found = service.findFieldsByLayout(layoutId);
        assertEquals(1, found.size());
        assertEquals("SingleField", found.getFirst().name());
    }

    @Test
    @Order(12)
    void replaceLayoutFieldsWithEmpty() {
        service.replaceLayoutFields(layoutId, List.of());
        assertTrue(service.findFieldsByLayout(layoutId).isEmpty());
    }

    @Test
    @Order(13)
    void fieldsHaveCorrectPositions() {
        var fields = List.of(
                new LayoutFieldEntry("First", EventFieldType.STRING, "{}", false, null),
                new LayoutFieldEntry("Second", EventFieldType.NUMBER, "{}", false, null),
                new LayoutFieldEntry("Third", EventFieldType.STRING, "{}", true, null));
        service.replaceLayoutFields(layoutId, fields);

        var found = service.findFieldsByLayout(layoutId);
        assertEquals(3, found.size());
        assertEquals(0, found.get(0).position());
        assertEquals(1, found.get(1).position());
        assertEquals(2, found.get(2).position());
    }

    @Test
    @Order(99)
    void deleteLayout() {
        assertTrue(service.delete(layoutId));
        assertTrue(service.findById(layoutId).isEmpty());
    }
}
