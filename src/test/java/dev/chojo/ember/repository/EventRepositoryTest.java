/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventCategory;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
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
class EventRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;
    private static int breakId;
    private static int categoryId;
    private static int registrationId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event Station");
        account = accountRepo.create("event@test.com", "Event", "User");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    // -- Categories --

    @Test
    @Order(1)
    void createCategory() {
        EventCategory cat = eventRepo.createCategory(station.id(), "Training", 1);
        assertNotNull(cat);
        assertEquals("Training", cat.name());
        assertEquals(1, cat.position());
        categoryId = cat.id();
    }

    @Test
    @Order(2)
    void findCategoriesByStation() {
        var cats = eventRepo.findCategoriesByStation(station.id());
        assertEquals(1, cats.size());
        assertEquals("Training", cats.getFirst().name());
    }

    @Test
    @Order(3)
    void updateCategory() {
        assertTrue(eventRepo.updateCategory(categoryId, "Exercise", 2, null, false));
        var cats = eventRepo.findCategoriesByStation(station.id());
        assertEquals("Exercise", cats.getFirst().name());
        assertEquals(2, cats.getFirst().position());
    }

    // -- Events --

    @Test
    @Order(10)
    void createOneTimeEvent() {
        Instant start = Instant.parse("2026-06-15T09:00:00Z");
        Instant end = Instant.parse("2026-06-15T12:00:00Z");
        StationEvent event = eventRepo.create(
                station.id(),
                "Fire Drill",
                "Annual drill",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                false,
                null,
                false,
                categoryId,
                null);
        assertNotNull(event);
        assertEquals("Fire Drill", event.name());
        assertEquals(StationEvent.EventType.ONE_TIME, event.eventType());
        assertEquals(categoryId, event.categoryId());
        eventId = event.id();
    }

    @Test
    @Order(11)
    void findById() {
        var event = eventRepo.findById(eventId);
        assertTrue(event.isPresent());
        assertEquals("Fire Drill", event.get().name());
    }

    @Test
    @Order(12)
    void findByStation() {
        var events = eventRepo.findByStation(station.id());
        assertEquals(1, events.size());
    }

    @Test
    @Order(13)
    void findByIdNotFound() {
        assertTrue(eventRepo.findById(99999).isEmpty());
    }

    @Test
    @Order(14)
    void update() {
        Instant start = Instant.parse("2026-07-01T10:00:00Z");
        Instant end = Instant.parse("2026-07-01T13:00:00Z");
        assertTrue(eventRepo.update(
                eventId,
                "Updated Drill",
                "Updated desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                true,
                categoryId,
                false,
                null));
        StationEvent updated = eventRepo.findById(eventId).orElseThrow();
        assertEquals("Updated Drill", updated.name());
        assertEquals("Updated desc", updated.description());
        assertTrue(updated.requiresRegistration());
        assertTrue(updated.requiresConfirmation());
    }

    @Test
    @Order(15)
    void createRecurringEvent() {
        Instant start = Instant.parse("2026-06-16T14:00:00Z");
        Instant end = Instant.parse("2026-06-16T15:00:00Z");
        StationEvent event = eventRepo.create(
                station.id(),
                "Weekly Meeting",
                "Team sync",
                StationEvent.EventType.RECURRING,
                1,
                start,
                end,
                null,
                false,
                null,
                false,
                null,
                null);
        assertNotNull(event);
        assertEquals(StationEvent.EventType.RECURRING, event.eventType());
        assertEquals(1, event.dayOfWeek());
        // cleanup
        eventRepo.delete(event.id());
    }

    // -- Breaks --

    @Test
    @Order(20)
    void createBreak() {
        EventBreak brk = eventRepo.createBreak(
                station.id(), "Summer Break", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));
        assertNotNull(brk);
        assertEquals("Summer Break", brk.name());
        assertEquals(LocalDate.of(2026, 7, 1), brk.startDate());
        breakId = brk.id();
    }

    @Test
    @Order(21)
    void findBreakById() {
        assertTrue(eventRepo.findBreakById(breakId).isPresent());
    }

    @Test
    @Order(22)
    void findBreaksByStation() {
        assertEquals(1, eventRepo.findBreaksByStation(station.id()).size());
    }

    @Test
    @Order(23)
    void updateBreak() {
        assertTrue(
                eventRepo.updateBreak(breakId, "Winter Break", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5)));
        EventBreak updated = eventRepo.findBreakById(breakId).orElseThrow();
        assertEquals("Winter Break", updated.name());
    }

    @Test
    @Order(24)
    void isDateInBreak() {
        assertTrue(eventRepo.isDateInBreak(station.id(), LocalDate.of(2026, 12, 25)));
        assertFalse(eventRepo.isDateInBreak(station.id(), LocalDate.of(2026, 6, 1)));
    }

    @Test
    @Order(25)
    void deleteBreak() {
        assertTrue(eventRepo.deleteBreak(breakId));
        assertTrue(eventRepo.findBreakById(breakId).isEmpty());
    }

    // -- Registrations --

    @Test
    @Order(30)
    void createRegistration() {
        LocalDate eventDate = LocalDate.of(2026, 7, 1);
        EventRegistration reg = eventRepo.createRegistration(eventId, member.id(), eventDate);
        assertNotNull(reg);
        assertEquals(eventId, reg.eventId());
        assertEquals(member.id(), reg.memberId());
        assertEquals(EventRegistration.RegistrationStatus.PENDING, reg.status());
        registrationId = reg.id();
    }

    @Test
    @Order(31)
    void findRegistrationById() {
        assertTrue(eventRepo.findRegistrationById(registrationId).isPresent());
    }

    @Test
    @Order(32)
    void findRegistrations() {
        var regs = eventRepo.findRegistrations(eventId, LocalDate.of(2026, 7, 1));
        assertEquals(1, regs.size());
    }

    @Test
    @Order(33)
    void findRegistrationsByMember() {
        assertFalse(eventRepo.findRegistrationsByMember(member.id()).isEmpty());
    }

    @Test
    @Order(34)
    void findPendingRegistrationsByStation() {
        var pending = eventRepo.findPendingRegistrationsByStation(station.id());
        assertEquals(1, pending.size());
        assertEquals(
                EventRegistration.RegistrationStatus.PENDING, pending.getFirst().status());
    }

    @Test
    @Order(35)
    void updateRegistrationStatus() {
        assertTrue(eventRepo.updateRegistrationStatus(registrationId, EventRegistration.RegistrationStatus.ACCEPTED));
        var reg = eventRepo.findRegistrationById(registrationId).orElseThrow();
        assertEquals(EventRegistration.RegistrationStatus.ACCEPTED, reg.status());
        // No longer pending
        assertTrue(eventRepo.findPendingRegistrationsByStation(station.id()).isEmpty());
    }

    @Test
    @Order(36)
    void deleteRegistration() {
        assertTrue(eventRepo.deleteRegistration(registrationId));
        assertTrue(eventRepo.findRegistrationById(registrationId).isEmpty());
    }

    // -- findByStationForMember --

    @Test
    @Order(37)
    void findByStationForMember() {
        var events = eventRepo.findByStationForMember(station.id(), member.id());
        assertNotNull(events);
    }

    // -- findCategoryById --

    @Test
    @Order(38)
    void findCategoryById() {
        assertTrue(eventRepo.findCategoryById(categoryId).isPresent());
        assertTrue(eventRepo.findCategoryById(99999).isEmpty());
    }

    // -- findAllRegistrations --

    @Test
    @Order(39)
    void findAllRegistrations() {
        // Create a registration first
        LocalDate date = LocalDate.of(2026, 8, 1);
        var reg = eventRepo.createRegistration(
                eventId, member.id(), date, EventRegistration.RegistrationStatus.ACCEPTED, member.id());
        var all = eventRepo.findAllRegistrations(eventId);
        assertFalse(all.isEmpty());
        eventRepo.deleteRegistration(reg.id());
    }

    // -- findPendingRegistrations --

    @Test
    @Order(40)
    void findPendingRegistrations() {
        LocalDate date = LocalDate.of(2026, 9, 1);
        var reg = eventRepo.createRegistration(eventId, member.id(), date);
        var pending = eventRepo.findPendingRegistrations(eventId);
        assertFalse(pending.isEmpty());
        eventRepo.deleteRegistration(reg.id());
    }

    // -- findRegistrationCounts --

    @Test
    @Order(41)
    void findRegistrationCounts() {
        LocalDate date = LocalDate.of(2026, 10, 1);
        var reg = eventRepo.createRegistration(eventId, member.id(), date);
        var counts = eventRepo.findRegistrationCounts(station.id());
        assertNotNull(counts);
        eventRepo.deleteRegistration(reg.id());
    }

    // -- findDeclinedMemberIds --

    @Test
    @Order(42)
    void findDeclinedMemberIds() {
        LocalDate date = LocalDate.of(2026, 11, 1);
        var reg = eventRepo.createRegistration(
                eventId, member.id(), date, EventRegistration.RegistrationStatus.DECLINED, null);
        var declined = eventRepo.findDeclinedMemberIds(eventId, date);
        assertTrue(declined.contains(member.id()));
        eventRepo.deleteRegistration(reg.id());
    }

    // -- findRegistrationStatsByEvent --

    @Test
    @Order(43)
    void findRegistrationStatsByEvent() {
        var stats = eventRepo.findRegistrationStatsByEvent(eventId, null, 12);
        assertNotNull(stats);
    }

    // -- markDeadlineNotified --

    @Test
    @Order(44)
    void markDeadlineNotified() {
        assertDoesNotThrow(() -> eventRepo.markDeadlineNotified(eventId));
    }

    // -- reorderCategories --

    @Test
    @Order(45)
    void reorderCategories() {
        var cat2 = eventRepo.createCategory(station.id(), "Cat2", 2);
        assertDoesNotThrow(() -> eventRepo.reorderCategories(List.of(categoryId, cat2.id())));
        eventRepo.deleteCategory(cat2.id());
    }

    // -- setFieldDefaults / findFieldDefaults --

    @Test
    @Order(46)
    void setAndFindFieldDefaults() {
        // event_field_default.field_id references attendance_template_field
        var template = attendanceRepo.createTemplate(station.id(), "FDTemplate");
        attendanceRepo.createTemplateField(
                template.id(), "SignedBy", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        var templateFields = attendanceRepo.findTemplateFields(template.id());
        int templateFieldId = templateFields.getFirst().id();

        var tmpEvent = eventRepo.create(
                station.id(),
                "FieldDefault Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2026-12-01T10:00:00Z"),
                Instant.parse("2026-12-01T12:00:00Z"),
                template.id(),
                false,
                null,
                false,
                null,
                null);
        var defaults = List.of(new EventFieldDefault(tmpEvent.id(), templateFieldId, "VALUE", "value1"));
        assertDoesNotThrow(() -> eventRepo.setFieldDefaults(tmpEvent.id(), defaults));
        var found = eventRepo.findFieldDefaults(tmpEvent.id());
        assertFalse(found.isEmpty());
        assertEquals(templateFieldId, found.getFirst().fieldId());
        // Clear
        eventRepo.setFieldDefaults(tmpEvent.id(), List.of());
        assertTrue(eventRepo.findFieldDefaults(tmpEvent.id()).isEmpty());
        eventRepo.delete(tmpEvent.id());
        attendanceRepo.deleteTemplate(template.id());
    }

    // -- updateRestrictionMode --

    @Test
    @Order(47)
    void updateRestrictionMode() {
        assertTrue(eventRepo.updateRestrictionMode(eventId, RestrictionMode.OR));
        assertTrue(eventRepo.updateRestrictionMode(eventId, RestrictionMode.AND));
    }

    // -- Cleanup --

    @Test
    @Order(90)
    void deleteCategory() {
        // Must delete event first since it references category
        assertTrue(eventRepo.delete(eventId));
        assertTrue(eventRepo.deleteCategory(categoryId));
        assertTrue(eventRepo.findCategoriesByStation(station.id()).isEmpty());
    }

    @Test
    @Order(91)
    void deleteEventNotFound() {
        assertFalse(eventRepo.delete(99999));
    }
}
