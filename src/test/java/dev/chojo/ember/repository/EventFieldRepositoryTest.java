/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFieldRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;
    private static int categoryId;
    private static int fieldId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("EventField Station");
        account = accountRepo.create("eventfield@test.com", "EF", "User");
        member = stationMemberRepo.create(station.id(), account.id());
        EventCategory cat = eventRepo.createCategory(station.id(), "EF Cat", 1);
        categoryId = cat.id();
        StationEvent event = eventRepo.create(
                station.id(),
                "EF Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                categoryId);
        eventId = event.id();
    }

    @AfterAll
    static void cleanup() {
        eventRepo.delete(eventId);
        eventRepo.deleteCategory(categoryId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        EventField field = eventFieldRepo.create(eventId, "Location", "string", "{}", "Berlin", 0, false, null);
        assertNotNull(field);
        assertEquals("Location", field.name());
        assertEquals("string", field.fieldType());
        assertEquals("Berlin", field.value());
        assertEquals(0, field.position());
        assertFalse(field.overview());
        fieldId = field.id();
    }

    @Test
    @Order(2)
    void findByEvent() {
        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(1, fields.size());
        assertEquals("Location", fields.getFirst().name());
    }

    @Test
    @Order(3)
    void findByEventEmpty() {
        assertTrue(eventFieldRepo.findByEvent(99999).isEmpty());
    }

    @Test
    @Order(4)
    void findDistinctFieldNames() {
        var names = eventFieldRepo.findDistinctFieldNames(station.id());
        assertEquals(1, names.size());
        assertEquals("Location", names.getFirst());
    }

    @Test
    @Order(10)
    void replaceFields() {
        eventFieldRepo.replaceFields(
                eventId,
                List.of(
                        new EventFieldRepository.FieldEntry("Key1", "string", "{}", "Val1", false, null),
                        new EventFieldRepository.FieldEntry("Key2", "string", "{}", "Val2", true, null)));
        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(2, fields.size());
        assertEquals("Key1", fields.get(0).name());
        assertEquals("Key2", fields.get(1).name());
        assertTrue(fields.get(1).overview());
    }

    @Test
    @Order(11)
    void deleteByEvent() {
        eventFieldRepo.deleteByEvent(eventId);
        assertTrue(eventFieldRepo.findByEvent(eventId).isEmpty());
    }
}
