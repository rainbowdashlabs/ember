/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventThresholdCheckerTest extends RepositoryTestBase {
    private static EventThresholdChecker checker;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        var eventBus = new DomainEventBus(Set.of());
        EventService eventService = new EventService(eventRepo, restrictionService, eventBus);
        checker = new EventThresholdChecker(eventRepo, eventService, new StationReadOnlyGuard(stationRepo));
        station = stationRepo.create("ThresholdChecker Station");
        account = accountRepo.create("threshold@test.com", "Threshold", "Checker");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void checkCancelsEventsNotMeetingThreshold() throws Exception {
        // Create event with threshold in the past and min_registrations=5 but 0 accepted
        var event = eventRepo.create(
                station.id(),
                "Below Threshold",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(90000),
                null,
                true,
                null,
                false,
                null,
                null,
                5,
                Instant.now().minusSeconds(3600),
                null);

        assertFalse(event.cancelled());

        // Invoke the private check method
        Method checkMethod = EventThresholdChecker.class.getDeclaredMethod("check");
        checkMethod.setAccessible(true);
        checkMethod.invoke(checker);

        // Verify the event was cancelled
        var updated = eventRepo.findById(event.id()).orElseThrow();
        assertTrue(updated.cancelled());
        assertNotNull(updated.cancelReason());
        assertTrue(updated.cancelReason().contains("5"));

        eventRepo.delete(event.id());
    }

    @Test
    @Order(2)
    void checkDoesNotCancelEventsMeetingThreshold() throws Exception {
        // Create event with threshold in the past but enough registrations
        var event = eventRepo.create(
                station.id(),
                "Meeting Threshold",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plusSeconds(86400),
                Instant.now().plusSeconds(90000),
                null,
                true,
                null,
                false,
                null,
                null,
                1,
                Instant.now().minusSeconds(3600),
                null);

        // Register a member with ACCEPTED status
        var reg = eventRepo.createRegistration(
                event.id(), member.id(), LocalDate.now(), RegistrationStatus.ACCEPTED, null);

        Method checkMethod = EventThresholdChecker.class.getDeclaredMethod("check");
        checkMethod.setAccessible(true);
        checkMethod.invoke(checker);

        var updated = eventRepo.findById(event.id()).orElseThrow();
        assertFalse(updated.cancelled());

        eventRepo.deleteRegistration(reg.id());
        eventRepo.delete(event.id());
    }

    @Test
    @Order(3)
    void checkWithNoEligibleEvents() throws Exception {
        // No events with thresholds — check should not fail
        Method checkMethod = EventThresholdChecker.class.getDeclaredMethod("check");
        checkMethod.setAccessible(true);
        assertDoesNotThrow(() -> checkMethod.invoke(checker));
    }
}
