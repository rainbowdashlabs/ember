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
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

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
        EventCategory cat = eventRepo.createCategory(station.id(), "Training", 1, "#ff6421");
        assertNotNull(cat);
        assertEquals("Training", cat.name());
        assertEquals(1, cat.position());
        assertEquals("#ff6421", cat.color());
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
        assertTrue(eventRepo.updateCategory(categoryId, "Exercise", 2, null, false, "#3694ff"));
        var cats = eventRepo.findCategoriesByStation(station.id());
        assertEquals("Exercise", cats.getFirst().name());
        assertEquals(2, cats.getFirst().position());
        assertEquals("#3694ff", cats.getFirst().color());
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
                null,
                null,
                null,
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
                null,
                null,
                null,
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
                null,
                null,
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
        assertEquals(RegistrationStatus.PENDING, reg.status());
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
        assertEquals(RegistrationStatus.PENDING, pending.getFirst().status());
    }

    @Test
    @Order(35)
    void updateRegistrationStatus() {
        assertTrue(eventRepo.updateRegistrationStatus(registrationId, RegistrationStatus.ACCEPTED));
        var reg = eventRepo.findRegistrationById(registrationId).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, reg.status());
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
        var reg = eventRepo.createRegistration(eventId, member.id(), date, RegistrationStatus.ACCEPTED, member.id());
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
        var reg = eventRepo.createRegistration(eventId, member.id(), date, RegistrationStatus.DECLINED, null);
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
        var cat2 = eventRepo.createCategory(station.id(), "Cat2", 2, null);
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
                null,
                null,
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

    // -- findFiltered --

    @Test
    @Order(92)
    void findFilteredNoFilters() {
        // Re-create event for remaining tests
        var tmpEvent = eventRepo.create(
                station.id(),
                "Filtered Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-01-15T09:00:00Z"),
                Instant.parse("2027-01-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var results = eventRepo.findFiltered(station.id(), null, null, null);
        assertTrue(results.stream().anyMatch(e -> e.id() == tmpEvent.id()));
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(93)
    void findFilteredByMember() {
        var cat = eventRepo.createCategory(station.id(), "FilterCat", 0, null);
        var tmpEvent = eventRepo.create(
                station.id(),
                "Member Filtered",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-02-15T09:00:00Z"),
                Instant.parse("2027-02-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                cat.id(),
                null,
                null,
                null,
                null);

        var results = eventRepo.findFiltered(station.id(), member.id(), null, null);
        assertNotNull(results);

        eventRepo.delete(tmpEvent.id());
        eventRepo.deleteCategory(cat.id());
    }

    @Test
    @Order(94)
    void findFilteredByCategory() {
        var cat = eventRepo.createCategory(station.id(), "FilterCat2", 0, null);
        var tmpEvent = eventRepo.create(
                station.id(),
                "Cat Filtered",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-03-15T09:00:00Z"),
                Instant.parse("2027-03-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                cat.id(),
                null,
                null,
                null,
                null);

        var results = eventRepo.findFiltered(station.id(), null, cat.id(), null);
        assertTrue(results.stream().anyMatch(e -> e.id() == tmpEvent.id()));
        assertTrue(results.stream().allMatch(e -> e.categoryId() != null && e.categoryId() == cat.id()));

        eventRepo.delete(tmpEvent.id());
        eventRepo.deleteCategory(cat.id());
    }

    @Test
    @Order(95)
    void findFilteredByRequiresRegistration() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Reg Filtered",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-04-15T09:00:00Z"),
                Instant.parse("2027-04-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var results = eventRepo.findFiltered(station.id(), null, null, true);
        assertTrue(results.stream().anyMatch(e -> e.id() == tmpEvent.id()));
        assertTrue(results.stream().allMatch(StationEvent::requiresRegistration));

        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(96)
    void findFilteredAllParams() {
        var cat = eventRepo.createCategory(station.id(), "AllFilterCat", 0, null);
        var tmpEvent = eventRepo.create(
                station.id(),
                "All Params",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-05-15T09:00:00Z"),
                Instant.parse("2027-05-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                cat.id(),
                null,
                null,
                null,
                null);

        var results = eventRepo.findFiltered(station.id(), member.id(), cat.id(), true);
        assertNotNull(results);

        eventRepo.delete(tmpEvent.id());
        eventRepo.deleteCategory(cat.id());
    }

    // -- Edge cases --

    @Test
    @Order(97)
    void updateNonExistentEvent() {
        assertFalse(eventRepo.update(
                99999,
                "X",
                "X",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now(),
                Instant.now(),
                null,
                false,
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null));
    }

    @Test
    @Order(98)
    void updateNonExistentBreak() {
        assertFalse(eventRepo.updateBreak(
                99999, "X", LocalDate.now(), LocalDate.now().plusDays(1)));
    }

    @Test
    @Order(99)
    void deleteNonExistentBreak() {
        assertFalse(eventRepo.deleteBreak(99999));
    }

    @Test
    @Order(100)
    void deleteNonExistentCategory() {
        assertFalse(eventRepo.deleteCategory(99999));
    }

    @Test
    @Order(101)
    void updateNonExistentRegistrationStatus() {
        assertFalse(eventRepo.updateRegistrationStatus(99999, RegistrationStatus.ACCEPTED));
    }

    @Test
    @Order(102)
    void deleteNonExistentRegistration() {
        assertFalse(eventRepo.deleteRegistration(99999));
    }

    @Test
    @Order(103)
    void findRegistrationByIdNotFound() {
        assertTrue(eventRepo.findRegistrationById(99999).isEmpty());
    }

    @Test
    @Order(104)
    void findBreakByIdNotFound() {
        assertTrue(eventRepo.findBreakById(99999).isEmpty());
    }

    @Test
    @Order(105)
    void updateRestrictionModeNotFound() {
        assertFalse(eventRepo.updateRestrictionMode(99999, RestrictionMode.OR));
    }

    @Test
    @Order(106)
    void updateCategoryNotFound() {
        assertFalse(eventRepo.updateCategory(99999, "X", 0, null, false, null));
    }

    @Test
    @Order(107)
    void findFieldDefaultsEmpty() {
        assertTrue(eventRepo.findFieldDefaults(99999).isEmpty());
    }

    @Test
    @Order(108)
    void findRegistrationsEmpty() {
        assertTrue(eventRepo.findRegistrations(99999, LocalDate.now()).isEmpty());
    }

    @Test
    @Order(109)
    void findAllRegistrationsEmpty() {
        assertTrue(eventRepo.findAllRegistrations(99999).isEmpty());
    }

    @Test
    @Order(110)
    void findPendingRegistrationsEmpty() {
        assertTrue(eventRepo.findPendingRegistrations(99999).isEmpty());
    }

    @Test
    @Order(111)
    void findRegistrationsByMemberEmpty() {
        assertTrue(eventRepo.findRegistrationsByMember(99999).isEmpty());
    }

    @Test
    @Order(112)
    void findDeclinedMemberIdsEmpty() {
        assertTrue(eventRepo.findDeclinedMemberIds(99999, LocalDate.now()).isEmpty());
    }

    @Test
    @Order(113)
    void isDateInBreakNoBreaks() {
        assertFalse(eventRepo.isDateInBreak(99999, LocalDate.now()));
    }

    @Test
    @Order(114)
    void createRegistrationWithStatusAndCreatedBy() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Status Reg Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-06-15T09:00:00Z"),
                Instant.parse("2027-06-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        var reg = eventRepo.createRegistration(
                tmpEvent.id(), member.id(), LocalDate.of(2027, 6, 15), RegistrationStatus.ACCEPTED, member.id());
        assertNotNull(reg);
        assertEquals(RegistrationStatus.ACCEPTED, reg.status());
        assertEquals(member.id(), reg.createdBy());

        eventRepo.deleteRegistration(reg.id());
        eventRepo.delete(tmpEvent.id());
    }

    // -- Cancellation & Threshold --

    @Test
    @Order(115)
    void cancelEvent() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Cancel Me",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-08-15T09:00:00Z"),
                Instant.parse("2027-08-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        assertTrue(eventRepo.cancelEvent(tmpEvent.id(), "Testing cancellation"));
        var cancelled = eventRepo.findById(tmpEvent.id()).orElseThrow();
        assertTrue(cancelled.cancelled());
        assertEquals("Testing cancellation", cancelled.cancelReason());
        assertNotNull(cancelled.cancelledAt());
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(116)
    void cancelEventNotFound() {
        assertFalse(eventRepo.cancelEvent(99999, "reason"));
    }

    @Test
    @Order(117)
    void findAutoCancel() {
        // Create event with threshold in the past and no registrations
        var tmpEvent = eventRepo.create(
                station.id(),
                "Auto Cancel Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                5,
                Instant.now().minusSeconds(3600),
                null);
        var candidates = eventRepo.findAutoCancel();
        assertTrue(candidates.stream().anyMatch(e -> e.id() == tmpEvent.id()));
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(118)
    void setThresholdNotified() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Threshold Notify Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-10-15T09:00:00Z"),
                Instant.parse("2027-10-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                3,
                Instant.now().plusSeconds(86400),
                null);
        assertTrue(eventRepo.setThresholdNotified(tmpEvent.id()));
        var updated = eventRepo.findById(tmpEvent.id()).orElseThrow();
        assertTrue(updated.thresholdNotified());
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(119)
    void countAcceptedRegistrations() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Count Accepted Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-11-15T09:00:00Z"),
                Instant.parse("2027-11-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        assertEquals(0, eventRepo.countAcceptedRegistrations(tmpEvent.id()));
        var reg = eventRepo.createRegistration(
                tmpEvent.id(), member.id(), LocalDate.of(2027, 11, 15), RegistrationStatus.ACCEPTED, null);
        assertEquals(1, eventRepo.countAcceptedRegistrations(tmpEvent.id()));
        eventRepo.deleteRegistration(reg.id());
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(120)
    void findRegisteredMemberIds() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Registered Ids Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-12-15T09:00:00Z"),
                Instant.parse("2027-12-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var reg = eventRepo.createRegistration(
                tmpEvent.id(), member.id(), LocalDate.of(2027, 12, 15), RegistrationStatus.ACCEPTED, null);
        var ids = eventRepo.findRegisteredMemberIds(tmpEvent.id());
        assertTrue(ids.contains(member.id()));
        eventRepo.deleteRegistration(reg.id());
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(121)
    void countPendingRegistrations() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Pending Count Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-01-15T09:00:00Z"),
                Instant.parse("2028-01-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        assertEquals(0, eventRepo.countPendingRegistrations(station.id()));
        var reg = eventRepo.createRegistration(
                tmpEvent.id(), member.id(), LocalDate.of(2028, 1, 15), RegistrationStatus.PENDING, null);
        assertTrue(eventRepo.countPendingRegistrations(station.id()) >= 1);
        eventRepo.deleteRegistration(reg.id());
        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(122)
    void createRegistrationUpsertConflict() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Upsert Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-07-15T09:00:00Z"),
                Instant.parse("2027-07-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        LocalDate date = LocalDate.of(2027, 7, 15);
        var reg1 = eventRepo.createRegistration(tmpEvent.id(), member.id(), date, RegistrationStatus.PENDING, null);
        assertEquals(RegistrationStatus.PENDING, reg1.status());

        // Upsert with different status
        var reg2 = eventRepo.createRegistration(
                tmpEvent.id(), member.id(), date, RegistrationStatus.ACCEPTED, member.id());
        assertEquals(RegistrationStatus.ACCEPTED, reg2.status());
        assertEquals(reg1.id(), reg2.id()); // Same row updated

        eventRepo.deleteRegistration(reg2.id());
        eventRepo.delete(tmpEvent.id());
    }

    // -- Reminders --

    @Test
    @Order(130)
    void replaceAndFindReminders() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "Reminder Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-03-15T09:00:00Z"),
                Instant.parse("2028-03-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        eventRepo.replaceReminders(tmpEvent.id(), List.of(1, 3, 7));
        var days = eventRepo.findReminderDays(tmpEvent.id());
        assertEquals(3, days.size());
        assertEquals(List.of(1, 3, 7), days);

        // Replace with different values
        eventRepo.replaceReminders(tmpEvent.id(), List.of(2));
        days = eventRepo.findReminderDays(tmpEvent.id());
        assertEquals(1, days.size());
        assertEquals(2, days.getFirst());

        // Clear
        eventRepo.replaceReminders(tmpEvent.id(), List.of());
        assertTrue(eventRepo.findReminderDays(tmpEvent.id()).isEmpty());

        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(131)
    void replaceRemindersWithDuplicates() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "DupeReminder",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-04-15T09:00:00Z"),
                Instant.parse("2028-04-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        // Duplicate values should be deduplicated via ON CONFLICT DO NOTHING
        eventRepo.replaceReminders(tmpEvent.id(), List.of(3, 3, 7));
        var days = eventRepo.findReminderDays(tmpEvent.id());
        assertEquals(2, days.size());
        assertTrue(days.contains(3));
        assertTrue(days.contains(7));

        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(132)
    void isReminderSentAndMarkSent() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "ReminderSent Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-05-15T09:00:00Z"),
                Instant.parse("2028-05-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        LocalDate eventDate = LocalDate.of(2028, 5, 15);
        assertFalse(eventRepo.isReminderSent(tmpEvent.id(), eventDate, 3));

        eventRepo.markReminderSent(tmpEvent.id(), eventDate, 3);
        assertTrue(eventRepo.isReminderSent(tmpEvent.id(), eventDate, 3));

        // Different daysBefore should still be false
        assertFalse(eventRepo.isReminderSent(tmpEvent.id(), eventDate, 1));

        // Marking again should not throw (ON CONFLICT DO NOTHING)
        assertDoesNotThrow(() -> eventRepo.markReminderSent(tmpEvent.id(), eventDate, 3));

        eventRepo.delete(tmpEvent.id());
    }

    @Test
    @Order(133)
    void findEventsWithReminders() {
        var event = eventRepo.create(
                station.id(),
                "ReminderListEvent",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-09-15T09:00:00Z"),
                Instant.parse("2028-09-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            eventRepo.replaceReminders(event.id(), List.of(1, 3));
            var result = eventRepo.findEventsWithReminders();
            assertEquals(
                    1,
                    result.stream().filter(e -> e.id() == event.id()).count(),
                    "EXISTS-based query should not duplicate the event row");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    @Order(134)
    void findReminderDaysEmpty() {
        assertTrue(eventRepo.findReminderDays(99999).isEmpty());
    }

    @Test
    @Order(134)
    void isReminderSentForDifferentDate() {
        var tmpEvent = eventRepo.create(
                station.id(),
                "ReminderDateEvent",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-06-15T09:00:00Z"),
                Instant.parse("2028-06-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        LocalDate date1 = LocalDate.of(2028, 6, 15);
        LocalDate date2 = LocalDate.of(2028, 6, 16);

        eventRepo.markReminderSent(tmpEvent.id(), date1, 1);
        assertTrue(eventRepo.isReminderSent(tmpEvent.id(), date1, 1));
        assertFalse(eventRepo.isReminderSent(tmpEvent.id(), date2, 1));

        eventRepo.delete(tmpEvent.id());
    }

    // -- Registration close days --

    @Test
    @Order(140)
    void createEventWithCloseDays() {
        var event = eventRepo.create(
                station.id(),
                "Close Days Event",
                "desc",
                StationEvent.EventType.RECURRING,
                3,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                2);
        assertNotNull(event);
        assertEquals(2, event.registrationCloseDays());
        eventRepo.delete(event.id());
    }

    @Test
    @Order(141)
    void findRecurringEventsWithCloseDays() {
        var event = eventRepo.create(
                station.id(),
                "Recurring Close",
                "desc",
                StationEvent.EventType.RECURRING,
                1,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                3);
        var result = eventRepo.findRecurringEventsWithCloseDays();
        assertTrue(result.stream().anyMatch(e -> e.id() == event.id()));
        eventRepo.delete(event.id());
    }

    @Test
    @Order(142)
    void findOneTimeEventsWithExpiredDeadline() {
        var pastDeadline = Instant.now().minus(1, ChronoUnit.HOURS);
        var event = eventRepo.create(
                station.id(),
                "Expired Deadline",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                true,
                pastDeadline,
                false,
                null,
                null,
                null,
                null,
                null);
        eventRepo.createRegistration(
                event.id(), member.id(), LocalDate.now().plusDays(1), RegistrationStatus.PENDING, null);
        var expired = eventRepo.findOneTimeEventsWithExpiredDeadline();
        assertTrue(expired.stream().anyMatch(e -> e.eventId() == event.id()));
        eventRepo.delete(event.id());
    }

    @Test
    @Order(144)
    void findRegistrationsByMembersEmpty() {
        assertTrue(eventRepo.findRegistrationsByMembers(List.of()).isEmpty());
    }

    @Test
    @Order(145)
    void findRegistrationsByMembersUnknown() {
        assertTrue(eventRepo.findRegistrationsByMembers(List.of(99999, 99998)).isEmpty());
    }

    @Test
    @Order(146)
    void findRegistrationsByMembersAggregates() {
        var account2 = accountRepo.create("evt-bulk-2@test.com", "Bulk", "Two");
        var member2 = stationMemberRepo.create(station.id(), account2.id());
        var event = eventRepo.create(
                station.id(),
                "Bulk Registrations",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2027-09-15T09:00:00Z"),
                Instant.parse("2027-09-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var date = LocalDate.of(2027, 9, 15);
        eventRepo.createRegistration(event.id(), member.id(), date, RegistrationStatus.ACCEPTED, null);
        eventRepo.createRegistration(event.id(), member2.id(), date, RegistrationStatus.PENDING, null);

        var regs = eventRepo.findRegistrationsByMembers(List.of(member.id(), member2.id()));
        assertEquals(2, regs.size());
        // Subset query still works
        var owner = eventRepo.findRegistrationsByMembers(List.of(member.id()));
        assertEquals(1, owner.size());
        assertEquals(member.id(), owner.getFirst().memberId());

        eventRepo.delete(event.id());
        stationMemberRepo.delete(member2.id());
        accountRepo.delete(account2.id());
    }

    @Test
    @Order(143)
    void findPendingRegistrationsForDate() {
        var event = eventRepo.create(
                station.id(),
                "Pending Date",
                "desc",
                StationEvent.EventType.RECURRING,
                1,
                Instant.parse("2026-06-15T09:00:00Z"),
                Instant.parse("2026-06-15T12:00:00Z"),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        var date = LocalDate.of(2026, 6, 22);
        eventRepo.createRegistration(event.id(), member.id(), date, RegistrationStatus.PENDING, null);
        var pending = eventRepo.findPendingRegistrationsForDate(event.id(), date);
        assertEquals(1, pending.size());
        assertEquals(member.id(), pending.getFirst().memberId());
        eventRepo.delete(event.id());
    }

    @Test
    @Order(150)
    void findMaxEventUpdatedAtForEmptyStationReturnsEpoch() {
        var emptyStation = stationRepo.create("Empty For Max");
        try {
            assertEquals(Instant.EPOCH, eventRepo.findMaxEventUpdatedAt(emptyStation.id()));
        } finally {
            stationRepo.delete(emptyStation.id());
        }
    }

    @Test
    @Order(151)
    void findMaxRegistrationCreatedAtIsEpochForEmptyMembers() {
        assertEquals(Instant.EPOCH, eventRepo.findMaxRegistrationCreatedAt(List.of()));
        assertEquals(Instant.EPOCH, eventRepo.findMaxRegistrationCreatedAt(List.of(99999)));
    }

    @Test
    @Order(159)
    void findPublicUidsByIdsAndFindByPublicUid() {
        assertTrue(eventRepo.findPublicUidsByIds(station.id(), List.of()).isEmpty());
        var event = eventRepo.create(
                station.id(),
                "PublicUidEvent",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.parse("2028-07-15T09:00:00Z"),
                Instant.parse("2028-07-15T12:00:00Z"),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        try {
            var map = eventRepo.findPublicUidsByIds(station.id(), List.of(event.id()));
            assertTrue(map.containsKey(event.id()));
            var uid = map.get(event.id());
            assertNotNull(uid);
            var byUid = eventRepo.findByPublicUid(station.id(), uid);
            assertTrue(byUid.isPresent());
            assertEquals(event.id(), byUid.orElseThrow().id());
            assertTrue(
                    eventRepo.findByPublicUid(station.id(), UUID.randomUUID()).isEmpty());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    @Test
    @Order(160)
    void searchForPicker() {
        var cat = eventRepo.createCategory(station.id(), "PickerCat", 0, null);
        eventRepo.updateCategory(cat.id(), "PickerCat", 0, null, true, null);
        var future = eventRepo.create(
                station.id(),
                "Future-Picker-Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(30, ChronoUnit.DAYS),
                Instant.now().plus(30, ChronoUnit.DAYS).plusSeconds(3600),
                null,
                false,
                null,
                false,
                cat.id(),
                null,
                null,
                null,
                null);
        var past = eventRepo.create(
                station.id(),
                "Past-Picker-Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().minus(60, ChronoUnit.DAYS),
                Instant.now().minus(60, ChronoUnit.DAYS).plusSeconds(3600),
                null,
                false,
                null,
                false,
                cat.id(),
                null,
                null,
                null,
                null);
        try {
            var futureMatches =
                    eventRepo.searchForPicker(station.id(), "Future", EventRepository.PickerMode.FUTURE, 20);
            assertTrue(futureMatches.stream().anyMatch(e -> "Future-Picker-Event".equals(e.name())));
            assertNotNull(futureMatches.getFirst().eventUid());
            assertNotNull(futureMatches.getFirst().startTime());

            var pastMatches = eventRepo.searchForPicker(station.id(), null, EventRepository.PickerMode.PAST, 20);
            assertTrue(pastMatches.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));

            var all = eventRepo.searchForPicker(station.id(), "  ", EventRepository.PickerMode.ALL, 20);
            assertTrue(all.stream().anyMatch(e -> "Future-Picker-Event".equals(e.name())));
            assertTrue(all.stream().anyMatch(e -> "Past-Picker-Event".equals(e.name())));
        } finally {
            eventRepo.delete(future.id());
            eventRepo.delete(past.id());
            eventRepo.deleteCategory(cat.id());
        }
    }

    @Test
    @Order(152)
    void findMaxStampsAdvanceAfterMutations() {
        var freshStation = stationRepo.create("Fresh Max Tracking");
        var acc = accountRepo.create("max-track@test.com", "Max", "Track");
        var member = stationMemberRepo.create(freshStation.id(), acc.id());
        try {
            var initial = eventRepo.findMaxEventUpdatedAt(freshStation.id());
            assertEquals(Instant.EPOCH, initial);

            var event = eventRepo.create(
                    freshStation.id(),
                    "Tracked",
                    "",
                    StationEvent.EventType.ONE_TIME,
                    null,
                    Instant.parse("2027-10-10T09:00:00Z"),
                    Instant.parse("2027-10-10T11:00:00Z"),
                    null,
                    true,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);
            var afterCreate = eventRepo.findMaxEventUpdatedAt(freshStation.id());
            assertTrue(afterCreate.isAfter(Instant.EPOCH), "create should bump the max updated_at");

            // Registration upsert bumps created_at; max should reflect that.
            var regBefore = eventRepo.findMaxRegistrationCreatedAt(List.of(member.id()));
            assertEquals(Instant.EPOCH, regBefore);
            eventRepo.createRegistration(
                    event.id(), member.id(), LocalDate.of(2027, 10, 10), RegistrationStatus.PENDING, null);
            var regAfter = eventRepo.findMaxRegistrationCreatedAt(List.of(member.id()));
            assertTrue(regAfter.isAfter(Instant.EPOCH), "registration insert should bump the stamp");

            eventRepo.delete(event.id());
        } finally {
            stationMemberRepo.delete(member.id());
            accountRepo.delete(acc.id());
            stationRepo.delete(freshStation.id());
        }
    }
}
