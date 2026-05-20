/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.members.entity.StationMember;
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
import java.time.temporal.ChronoUnit;

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
        service = new EventService(eventRepo);
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
                categoryId);
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
                categoryId);
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
                categoryId);

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
    @Order(40)
    void deleteEvent() {
        assertTrue(service.delete(eventId));
        assertTrue(service.findById(eventId).isEmpty());
    }
}
