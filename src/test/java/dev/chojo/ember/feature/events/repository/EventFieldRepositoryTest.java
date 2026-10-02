/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFieldRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static int eventId;
    private static int categoryId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("EventField Station");
        account = accountRepo.create("eventfield@test.com", "EF", "User");
        StationMember member = stationMemberRepo.create(station.id(), account.id());
        EventCategory cat = eventCategoryRepo.create(station.id(), "EF Cat", 1, null);
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
                categoryId,
                null,
                null,
                null,
                null);
        eventId = event.id();
    }

    @AfterAll
    static void cleanup() {
        eventRepo.delete(eventId);
        eventCategoryRepo.delete(categoryId);
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void create() {
        AppointmentField field = eventFieldRepo.create(
                eventId,
                "Location",
                FieldType.TEXT,
                EventQuestionSettings.parse("{}"),
                "Berlin",
                0,
                false,
                null,
                false);
        assertNotNull(field);
        assertEquals("Location", field.name());
        assertEquals(FieldType.TEXT, field.fieldType());
        assertEquals("Berlin", field.value());
        assertEquals(0, field.position());
        assertFalse(field.overview());
        int fieldId = field.id();
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
                        new EventFieldDraft(
                                "Key1", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Val1", false, null, false),
                        new EventFieldDraft(
                                "Key2", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Val2", true, null, false)));
        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(2, fields.size());
        assertEquals("Key1", fields.get(0).name());
        assertEquals("Key2", fields.get(1).name());
        assertTrue(fields.get(1).overview());
    }

    @Test
    @Order(10)
    void locationFieldTypeRoundTrips() {
        eventFieldRepo.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        "Ort",
                        FieldType.LOCATION,
                        EventQuestionSettings.parse("{}"),
                        "Marktplatz 1",
                        true,
                        null,
                        true)));
        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(1, fields.size());
        assertEquals(FieldType.LOCATION, fields.getFirst().fieldType());
        assertEquals("Marktplatz 1", fields.getFirst().value());
    }

    @Test
    @Order(10)
    void findByIdAndUpdateValue() {
        var created = eventFieldRepo.create(
                eventId,
                "Toggle",
                FieldType.MEMBER,
                EventQuestionSettings.parse("{\"selfRegistration\":true}"),
                "",
                5,
                false,
                null,
                false);

        var loaded = eventFieldRepo.findById(created.id());
        assertTrue(loaded.isPresent());
        assertEquals("Toggle", loaded.get().name());
        assertTrue(loaded.get().config().selfRegistration());

        eventFieldRepo.updateValue(created.id(), "42");
        var reloaded = eventFieldRepo.findById(created.id());
        assertTrue(reloaded.isPresent());
        assertEquals("42", reloaded.get().value());
    }

    @Test
    @Order(10)
    void findByIdMissingReturnsEmpty() {
        assertTrue(eventFieldRepo.findById(987654).isEmpty());
    }

    /** The answer one named question carries, out of everything the appointment happens to ask. */
    private static String valueOf(List<AppointmentField> fields, String name) {
        return fields.stream()
                .filter(field -> name.equals(field.name()))
                .map(AppointmentField::value)
                .findFirst()
                .orElseThrow();
    }

    /** A question answered per date carries a different answer on each of them, and none elsewhere. */
    @Test
    @Order(10)
    void answersPerDateStandApart() {
        var field = eventFieldRepo.create(
                eventId,
                "Fahrer",
                FieldType.TEXT,
                EventQuestionSettings.parse("{\"perDate\":true}"),
                "ignored",
                0,
                true,
                null,
                false);
        LocalDate monday = LocalDate.of(2027, 3, 1);
        LocalDate tuesday = monday.plusDays(1);

        eventFieldRepo.updateValueOn(field.id(), monday, "Anna");
        eventFieldRepo.updateValueOn(field.id(), tuesday, "Bert");
        eventFieldRepo.updateValueOn(field.id(), monday, "Clara");

        assertEquals(
                "Clara",
                eventFieldRepo.findByIdOn(field.id(), monday).orElseThrow().value());
        assertEquals(
                "Bert",
                eventFieldRepo.findByIdOn(field.id(), tuesday).orElseThrow().value());
        assertEquals(
                "",
                eventFieldRepo
                        .findByIdOn(field.id(), monday.plusMonths(1))
                        .orElseThrow()
                        .value());

        assertEquals("Clara", valueOf(eventFieldRepo.findByEventOn(eventId, monday), "Fahrer"));

        var stored = eventFieldRepo.findDateValues(eventId);
        assertEquals(2, stored.get(field.id()).size());

        eventFieldRepo.deleteByEvent(eventId);
    }

    /** A question that is not answered per date reads the same answer on every one of them. */
    @Test
    @Order(10)
    void oneAnswerHoldsForEveryDateWhereTheQuestionSaysNothing() {
        eventFieldRepo.create(
                eventId,
                "Treffpunkt",
                FieldType.TEXT,
                EventQuestionSettings.parse("{}"),
                "Halle",
                0,
                true,
                null,
                false);
        LocalDate day = LocalDate.of(2027, 4, 5);

        assertEquals("Halle", valueOf(eventFieldRepo.findByEventOn(eventId, day), "Treffpunkt"));
        assertEquals(
                "Halle",
                valueOf(eventFieldRepo.findOverviewFieldsByEventsOn(List.of(eventId), List.of(day)), "Treffpunkt"));
        assertTrue(eventFieldRepo
                .findOverviewFieldsByEventsOn(List.of(), List.of())
                .isEmpty());
        assertTrue(eventFieldRepo.findEventIdsWithMemberFields().stream().noneMatch(id -> id == eventId));

        eventFieldRepo.deleteByEvent(eventId);
    }

    /**
     * Saving the questions again writes over the rows that are already there, which is what keeps
     * the answers given per date: they hang off the question's id.
     */
    @Test
    @Order(10)
    void savingAgainKeepsTheRowOfAQuestionTheEditorNames() {
        eventFieldRepo.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        "Fahrer", FieldType.TEXT, EventQuestionSettings.parse("{}"), "Anna", false, null, false)));
        int fieldId = eventFieldRepo.findByEvent(eventId).getFirst().id();

        eventFieldRepo.replaceFields(
                eventId,
                List.of(
                        new EventFieldDraft(
                                fieldId,
                                "Fahrerin",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "Bea",
                                true,
                                null,
                                false),
                        new EventFieldDraft(
                                "Beifahrer",
                                FieldType.TEXT,
                                EventQuestionSettings.parse("{}"),
                                "Cem",
                                false,
                                null,
                                false)));

        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(2, fields.size());
        assertEquals(fieldId, fields.getFirst().id());
        assertEquals("Fahrerin", fields.getFirst().name());
        assertEquals("Bea", fields.getFirst().value());
        assertTrue(fields.getFirst().overview());
        assertEquals("Beifahrer", fields.get(1).name());

        eventFieldRepo.deleteByEvent(eventId);
    }

    /** A question the editor claims but that belongs to another appointment is added, never stolen. */
    @Test
    @Order(10)
    void aQuestionOfAnotherAppointmentIsNotWrittenOver() {
        eventFieldRepo.replaceFields(
                eventId,
                List.of(new EventFieldDraft(
                        987654, "Fremd", FieldType.TEXT, EventQuestionSettings.parse("{}"), "x", false, null, false)));

        var fields = eventFieldRepo.findByEvent(eventId);
        assertEquals(1, fields.size());
        assertNotEquals(987654, fields.getFirst().id());

        eventFieldRepo.deleteByEvent(eventId);
    }

    @Test
    @Order(11)
    void deleteByEvent() {
        eventFieldRepo.deleteByEvent(eventId);
        assertTrue(eventFieldRepo.findByEvent(eventId).isEmpty());
    }
}
