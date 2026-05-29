/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.events.entity.EventFieldDefault;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventServiceTest extends RepositoryTestBase {
    private static EventService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;
    private static int categoryId;

    @BeforeAll
    static void setup() {
        service = new EventService(eventRepo, new RestrictionRepository(), new DomainEventBus(Set.of()));
        station = stationRepo.create("EventStation");
        account = accountRepo.create("event-svc@test.com", "Event", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void createCategory() {
        var cat = service.createCategory(station.id(), "Training", 0);
        assertNotNull(cat);
        assertEquals("Training", cat.name());
        categoryId = cat.id();
    }

    @Test
    @Order(2)
    void findCategories() {
        var cats = service.findCategoriesByStation(station.id());
        assertTrue(cats.stream().anyMatch(c -> c.id() == categoryId));
    }

    @Test
    @Order(10)
    void createEvent() {
        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Weekly Training",
                "Regular training session",
                StationEvent.EventType.RECURRING,
                3,
                start,
                end,
                null,
                false,
                null,
                false,
                categoryId,
                null);
        assertNotNull(event);
        assertEquals("Weekly Training", event.name());
        assertEquals(3, event.dayOfWeek());
        eventId = event.id();
    }

    @Test
    @Order(11)
    void findById() {
        assertTrue(service.findById(eventId).isPresent());
        assertTrue(service.findById(999999).isEmpty());
    }

    @Test
    @Order(12)
    void findByStation() {
        var events = service.findByStation(station.id());
        assertTrue(events.stream().anyMatch(e -> e.id() == eventId));
    }

    @Test
    @Order(13)
    void updateEvent() {
        var start = Instant.now().plus(2, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var result = service.update(
                eventId,
                "Updated Training",
                "New desc",
                StationEvent.EventType.RECURRING,
                4,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                false,
                null);
        assertTrue(result.isPresent());
        var updated = result.get();
        assertEquals("Updated Training", updated.name());
        assertEquals(4, updated.dayOfWeek());
        assertTrue(updated.requiresRegistration());
    }

    @Test
    @Order(20)
    void registerAndListRegistrations() {
        // Make event require registration first
        var start = Instant.now().plus(2, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        service.update(
                eventId,
                "Updated Training",
                "Desc",
                StationEvent.EventType.RECURRING,
                4,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                false,
                null);

        var reg = service.register(eventId, member.id(), LocalDate.of(2026, 6, 1), false, null);
        assertNotNull(reg);
        assertEquals(member.id(), reg.memberId());

        var regs = service.findAllRegistrations(eventId);
        assertTrue(regs.stream().anyMatch(r -> r.memberId() == member.id()));
    }

    @Test
    @Order(21)
    void myRegistrations() {
        var mine = service.findRegistrationsByMember(member.id());
        assertFalse(mine.isEmpty());
    }

    @Test
    @Order(30)
    void createBreak() {
        var br = service.createBreak(station.id(), "Summer", LocalDate.of(2026, 7, 1), LocalDate.of(2026, 8, 31));
        assertNotNull(br);
        var breaks = service.findBreaksByStation(station.id());
        assertTrue(breaks.stream().anyMatch(b -> b.id() == br.id()));
    }

    @Test
    @Order(31)
    void findBreakById() {
        var br = service.createBreak(station.id(), "Winter", LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 5));
        var found = service.findBreakById(br.id());
        assertTrue(found.isPresent());
        assertEquals(br.id(), found.get().id());
        assertEquals("Winter", found.get().name());
    }

    @Test
    @Order(32)
    void updateBreak() {
        var br = service.createBreak(station.id(), "Old Break", LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 7));
        var updated = service.updateBreak(br.id(), "New Break", LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 8));
        assertTrue(updated.isPresent());
        assertEquals("New Break", updated.get().name());
        assertEquals(LocalDate.of(2026, 11, 2), updated.get().startDate());
    }

    @Test
    @Order(33)
    void deleteBreak() {
        var br = service.createBreak(station.id(), "Short Break", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));
        assertTrue(service.deleteBreak(br.id()));
        assertTrue(service.findBreakById(br.id()).isEmpty());
    }

    @Test
    @Order(34)
    void updateBreakNotFound() {
        var updated = service.updateBreak(
                999999, "X", LocalDate.now(), LocalDate.now().plusDays(1));
        assertTrue(updated.isEmpty());
    }

    @Test
    @Order(40)
    void deleteEvent() {
        assertTrue(service.delete(eventId));
        assertTrue(service.findById(eventId).isEmpty());
    }

    @Test
    @Order(41)
    void deleteEventNotFound() {
        assertFalse(service.delete(999999));
    }

    // -- Category extras --

    @Test
    @Order(50)
    void findCategoryById() {
        var found = service.findCategoryById(categoryId);
        assertTrue(found.isPresent());
        assertEquals(categoryId, found.get().id());
    }

    @Test
    @Order(51)
    void findCategoryByIdMissing() {
        assertTrue(service.findCategoryById(999999).isEmpty());
    }

    @Test
    @Order(52)
    void updateCategory() {
        boolean updated = service.updateCategory(categoryId, "Updated Training", 1, null, false);
        assertTrue(updated);
        var found = service.findCategoryById(categoryId).orElseThrow();
        assertEquals("Updated Training", found.name());
    }

    @Test
    @Order(53)
    void reorderCategories() {
        var cat2 = service.createCategory(station.id(), "Category 2", 1);
        // Reorder: put cat2 first, categoryId second
        service.reorderCategories(List.of(cat2.id(), categoryId));
        // Just verify no exception is thrown and both still exist
        assertTrue(service.findCategoryById(categoryId).isPresent());
        assertTrue(service.findCategoryById(cat2.id()).isPresent());
        service.deleteCategory(cat2.id());
    }

    @Test
    @Order(54)
    void deleteCategory() {
        var cat = service.createCategory(station.id(), "Temp Cat", 99);
        assertTrue(service.deleteCategory(cat.id()));
        assertTrue(service.findCategoryById(cat.id()).isEmpty());
    }

    // -- Registration extras --

    @Test
    @Order(60)
    void updateRegistrationStatus() {
        var start = Instant.now().plus(10, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Status Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                true,
                categoryId,
                null);

        var reg = service.register(event.id(), member.id(), LocalDate.of(2026, 10, 1), false, null);
        assertEquals(RegistrationStatus.PENDING, reg.status());

        boolean updated = service.updateRegistrationStatus(reg.id(), RegistrationStatus.ACCEPTED);
        assertTrue(updated);

        var found = service.findRegistrationById(reg.id()).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, found.status());
    }

    @Test
    @Order(61)
    void withdrawRegistration() {
        var start = Instant.now().plus(11, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Withdraw Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                null);

        var reg = service.register(event.id(), member.id(), LocalDate.of(2026, 10, 2), false, null);
        assertTrue(service.withdrawRegistration(reg.id()));
        assertTrue(service.findRegistrationById(reg.id()).isEmpty());
    }

    @Test
    @Order(62)
    void declineRegistration() {
        var start = Instant.now().plus(12, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Decline Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                null);

        var reg = service.decline(event.id(), member.id(), LocalDate.of(2026, 10, 3), null);
        assertNotNull(reg);
        assertEquals(RegistrationStatus.DECLINED, reg.status());
    }

    @Test
    @Order(63)
    void findRegistrationsByDate() {
        var start = Instant.now().plus(13, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Date Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                null);

        LocalDate date = LocalDate.of(2026, 11, 1);
        service.register(event.id(), member.id(), date, false, null);

        var regs = service.findRegistrations(event.id(), date);
        assertFalse(regs.isEmpty());
        assertTrue(regs.stream().anyMatch(r -> r.memberId() == member.id()));
    }

    @Test
    @Order(64)
    void findPendingRegistrationsByStation() {
        var pending = service.findPendingRegistrationsByStation(station.id());
        // There should be at least the registrations created in order 20 and 63 that are still PENDING
        assertNotNull(pending);
    }

    @Test
    @Order(65)
    void findRegistrationCounts() {
        var counts = service.findRegistrationCounts(station.id());
        assertNotNull(counts);
    }

    // -- Restrictions --

    @Test
    @Order(70)
    void setAndFindRestrictions() {
        var start = Instant.now().plus(20, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Restricted Event",
                "desc",
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

        service.setRestrictions(event.id(), List.of(1), List.of(), List.of(), List.of());
        var restrictions = service.findRestrictions(event.id());
        assertNotNull(restrictions);
    }

    @Test
    @Order(71)
    void updateRestrictionMode() {
        var start = Instant.now().plus(21, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Mode Event",
                "desc",
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

        // Should not throw
        service.updateRestrictionMode(event.id(), RestrictionMode.OR);
        service.updateRestrictionMode(event.id(), RestrictionMode.AND);
    }

    @Test
    @Order(72)
    void isMemberEligibleNoRestrictions() {
        var start = Instant.now().plus(22, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Open Event",
                "desc",
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

        // No restrictions set — member should be eligible
        assertTrue(service.isMemberEligible(event.id(), member.id()));
    }

    // -- Field defaults --

    @Test
    @Order(80)
    void findFieldDefaultsEmptyWhenNoneSet() {
        var start = Instant.now().plus(30, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Field Default Event",
                "desc",
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

        // No defaults set — should be empty
        var found = service.findFieldDefaults(event.id());
        assertTrue(found.isEmpty());

        // Set empty defaults — should remain empty
        service.setFieldDefaults(event.id(), List.of());
        assertTrue(service.findFieldDefaults(event.id()).isEmpty());
    }

    @Test
    @Order(81)
    void resolveFieldDefaultsEmptyWhenNoDefaults() {
        var start = Instant.now().plus(31, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Resolve Event No Defaults",
                "Desc",
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

        // No defaults set — resolve returns empty map
        var resolved = service.resolveFieldDefaults(event.id());
        assertTrue(resolved.isEmpty());
    }

    @Test
    @Order(82)
    void resolveFieldDefaultsForMissingEvent() {
        var resolved = service.resolveFieldDefaults(999999);
        assertTrue(resolved.isEmpty());
    }

    // -- findByStationForMember --

    @Test
    @Order(90)
    void findByStationForMember() {
        var events = service.findByStationForMember(station.id(), member.id());
        assertNotNull(events);
    }

    @Test
    @Order(91)
    void autoAcceptRegistration() {
        var start = Instant.now().plus(40, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "AutoAccept Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                null);

        var reg = service.register(event.id(), member.id(), LocalDate.of(2027, 1, 1), true, null);
        assertEquals(RegistrationStatus.ACCEPTED, reg.status());
    }

    @Test
    @Order(92)
    void findDeclinedMemberIds() {
        var start = Instant.now().plus(41, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Declined IDs Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                end,
                null,
                true,
                null,
                false,
                categoryId,
                null);

        LocalDate date = LocalDate.of(2027, 2, 1);
        service.decline(event.id(), member.id(), date, null);
        var declined = service.findDeclinedMemberIds(event.id(), date);
        assertTrue(declined.contains(member.id()));
    }

    @Test
    @Order(93)
    void findRegistrationStats() {
        var stats = service.findRegistrationStats(0, null, 6);
        assertNotNull(stats);
    }

    @Test
    @Order(94)
    void updateNonExistentEvent() {
        var result = service.update(
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
                null);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(95)
    void findRestrictionsForMissingEvent() {
        var restrictions = service.findRestrictions(99999);
        assertNotNull(restrictions);
    }

    @Test
    @Order(96)
    void setRestrictionsWithNulls() {
        var start = Instant.now().plus(50, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Null Restrictions",
                "desc",
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

        // All null lists — should default to empty
        service.setRestrictions(event.id(), null, null, null, null);
        var restrictions = service.findRestrictions(event.id());
        assertNotNull(restrictions);
    }

    @Test
    @Order(97)
    void findTodayEvents() {
        // This relies on the current date, but should at least not throw
        var today = service.findTodayEvents(station.id());
        assertNotNull(today);
    }

    @Test
    @Order(98)
    void resolveFieldDefaultsWithAttendanceField() {
        // Create an attendance template with two fields
        var template = attendanceRepo.createTemplate(station.id(), "EventDefaultTemplate");
        attendanceRepo.createTemplateField(
                template.id(), "FieldA", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        attendanceRepo.createTemplateField(
                template.id(), "FieldB", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 1);
        var fields = attendanceRepo.findTemplateFields(template.id());
        assertFalse(fields.isEmpty());
        int fieldAId = fields.get(0).id();
        int fieldBId = fields.get(1).id();

        var start = Instant.now().plus(70, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Value Source Event",
                "My Desc",
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

        // Use separate field IDs — no duplicates
        service.setFieldDefaults(
                event.id(),
                List.of(
                        new EventFieldDefault(event.id(), fieldAId, "VALUE", "\"hardcoded\""),
                        new EventFieldDefault(event.id(), fieldBId, "EVENT_NAME", null)));

        var resolved = service.resolveFieldDefaults(event.id());
        assertNotNull(resolved);
        assertEquals("\"hardcoded\"", resolved.get(fieldAId));
        assertEquals("Value Source Event", resolved.get(fieldBId));
    }

    @Test
    @Order(99)
    void resolveFieldDefaultsWithUnknownSource() {
        // Using an unknown source type — should be skipped (returns null, not added to result)
        var template = attendanceRepo.createTemplate(station.id(), "EventDefaultTemplate2");
        attendanceRepo.createTemplateField(
                template.id(), "TestField2", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        var fields = attendanceRepo.findTemplateFields(template.id());
        int attendanceFieldId = fields.getFirst().id();

        var start = Instant.now().plus(75, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Unknown Source Event",
                "desc",
                StationEvent.EventType.RECURRING,
                1,
                start,
                end,
                null,
                false,
                null,
                false,
                categoryId,
                null);

        service.setFieldDefaults(
                event.id(), List.of(new EventFieldDefault(event.id(), attendanceFieldId, "UNKNOWN_SOURCE", null)));

        var resolved = service.resolveFieldDefaults(event.id());
        // UNKNOWN_SOURCE returns null, so field should NOT be in result
        assertFalse(resolved.containsKey(attendanceFieldId));
    }

    @Test
    @Order(100)
    void resolveFieldDefaultsWithEventDescriptionSource() {
        var template = attendanceRepo.createTemplate(station.id(), "EventDefaultTemplate3");
        attendanceRepo.createTemplateField(
                template.id(), "TestField3", AttendanceFieldType.STRING, AttendanceFieldConfig.parse("{}"), 0);
        var fields = attendanceRepo.findTemplateFields(template.id());
        int attendanceFieldId = fields.getFirst().id();

        var start = Instant.now().plus(72, ChronoUnit.DAYS);
        var end = start.plus(2, ChronoUnit.HOURS);
        var event = service.create(
                station.id(),
                "Desc Source Event",
                "My Description",
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

        service.setFieldDefaults(
                event.id(), List.of(new EventFieldDefault(event.id(), attendanceFieldId, "EVENT_DESCRIPTION", null)));

        var resolved = service.resolveFieldDefaults(event.id());
        assertNotNull(resolved);
        assertEquals("My Description", resolved.get(attendanceFieldId));
    }

    @Test
    @Order(101)
    void updateRegistrationStatusNotFound() {
        assertFalse(service.updateRegistrationStatus(999999, RegistrationStatus.ACCEPTED));
    }

    @Test
    @Order(102)
    void findTodayEventsWithOneTimeMatchingToday() {
        // Create a ONE_TIME event with start time = today UTC
        var todayStart =
                LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        var todayEnd = todayStart.plus(2, ChronoUnit.HOURS);

        var event = service.create(
                station.id(),
                "Today ONE_TIME",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                todayStart,
                todayEnd,
                null,
                false,
                null,
                false,
                categoryId,
                null);

        var today = service.findTodayEvents(station.id());
        assertTrue(today.stream().anyMatch(e -> e.id() == event.id()));
    }
}
