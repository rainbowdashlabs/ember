/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventTemplateFieldData;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.events.service.EventTemplateService;
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
class EventTemplateServiceTest extends RepositoryTestBase {

    private static EventTemplateService service;
    private static Station station;
    private static int templateId;

    @BeforeAll
    static void setup() {
        service = new EventTemplateService(new EventTemplateRepository());
        station = stationRepo.create("EventTemplateServiceStation");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
    }

    @Test
    @Order(1)
    void createTemplate() {
        var template = service.create(station.id(), "Training Template");
        assertNotNull(template);
        assertEquals("Training Template", template.name());
        assertEquals(station.id(), template.stationId());
        templateId = template.id();
    }

    @Test
    @Order(2)
    void findById() {
        var found = service.findById(templateId);
        assertTrue(found.isPresent());
        assertEquals(templateId, found.get().id());
        assertEquals("Training Template", found.get().name());
    }

    @Test
    @Order(3)
    void findByIdMissing() {
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(4)
    void findByStation() {
        var templates = service.findByStation(station.id());
        assertTrue(templates.stream().anyMatch(t -> t.id() == templateId));
    }

    @Test
    @Order(5)
    void updateTemplate() {
        boolean updated = service.update(
                templateId,
                "Updated Template",
                "Weekly Training",
                "Regular training session",
                null,
                "RECURRING",
                true,
                "1 day",
                false,
                "AND",
                null,
                null);
        assertTrue(updated);
        var found = service.findById(templateId).orElseThrow();
        assertEquals("Updated Template", found.name());
        assertEquals("Weekly Training", found.title());
    }

    @Test
    @Order(10)
    void replaceAndFindFields() {
        var fields = List.of(
                new EventTemplateFieldData("Location", EventFieldType.STRING, "{}", 0, true, false, null),
                new EventTemplateFieldData("Notes", EventFieldType.STRING, "{}", 1, false, true, null));
        service.replaceFields(templateId, fields);

        var found = service.findFields(templateId);
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Location") && f.overview()));
        assertTrue(found.stream().anyMatch(f -> f.name().equals("Notes") && f.isPublic()));
    }

    @Test
    @Order(11)
    void replaceFieldsClearsOld() {
        service.replaceFields(
                templateId,
                List.of(new EventTemplateFieldData("OnlyField", EventFieldType.STRING, "{}", 0, false, false, null)));

        var found = service.findFields(templateId);
        assertEquals(1, found.size());
        assertEquals("OnlyField", found.getFirst().name());
    }

    @Test
    @Order(12)
    void replaceFieldsWithEmpty() {
        service.replaceFields(templateId, List.of());
        assertTrue(service.findFields(templateId).isEmpty());
    }

    @Test
    @Order(20)
    void setAndFindRestrictions() {
        service.setRestrictions(templateId, List.of(1, 2, 3));
        var restrictions = service.findRestrictions(templateId);
        assertEquals(3, restrictions.size());
        assertTrue(restrictions.containsAll(List.of(1, 2, 3)));
    }

    @Test
    @Order(21)
    void setRestrictionsReplaces() {
        service.setRestrictions(templateId, List.of(5));
        var restrictions = service.findRestrictions(templateId);
        assertEquals(1, restrictions.size());
        assertEquals(5, restrictions.getFirst());
    }

    @Test
    @Order(22)
    void setRestrictionsEmpty() {
        service.setRestrictions(templateId, List.of());
        var restrictions = service.findRestrictions(templateId);
        assertTrue(restrictions.isEmpty());
    }

    @Test
    @Order(99)
    void deleteTemplate() {
        assertTrue(service.delete(templateId));
        assertTrue(service.findById(templateId).isEmpty());
    }
}
