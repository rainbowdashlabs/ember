/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
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
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventCancelledHandlerTest extends RepositoryTestBase {
    private static EventCancelledHandler handler;
    private static NotificationService notificationService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int eventId;

    @BeforeAll
    static void setup() {
        notificationService = mock(NotificationService.class);
        handler = new EventCancelledHandler(notificationService, eventRegistrationRepo);
        station = stationRepo.create("CancelledHandler Station");
        account = accountRepo.create("cancelled-handler@test.com", "Cancel", "Handler");
        member = stationMemberRepo.create(station.id(), account.id());

        var event = eventRepo.create(
                station.id(),
                "Cancel Handler Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS),
                Instant.now().plus(1, ChronoUnit.DAYS).plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventId = event.id();

        // Register the member for this event
        eventRegistrationRepo.create(event.id(), member.id(), LocalDate.now(), RegistrationStatus.ACCEPTED, null);
    }

    @AfterAll
    static void cleanup() {
        eventRepo.delete(eventId);
        stationMemberRepo.delete(member.id());
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void eventType() {
        assertEquals(EventCancelled.class, handler.eventType());
    }

    @Test
    @Order(2)
    void handleNotifiesRegisteredMembers() {
        var event = new EventCancelled(station.id(), eventId, "Cancel Handler Event", "Testing cancellation");
        handler.handle(event);

        verify(notificationService, atLeastOnce())
                .notify(eq(member.id()), eq(NotificationType.EVENT_CANCELLED), any(NotificationData.class));
    }

    @Test
    @Order(3)
    void handleWithNoRegistrations() {
        // Create event with no registrations
        var emptyEvent = eventRepo.create(
                station.id(),
                "No Reg Event",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(5, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);

        reset(notificationService);
        var cancelledEvent = new EventCancelled(station.id(), emptyEvent.id(), "No Reg Event", "No members");
        handler.handle(cancelledEvent);

        // Should not notify anyone since no registrations
        verifyNoInteractions(notificationService);
        eventRepo.delete(emptyEvent.id());
    }
}
