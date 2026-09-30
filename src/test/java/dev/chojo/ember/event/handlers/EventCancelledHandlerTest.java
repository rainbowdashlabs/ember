/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.event.events.EventDateRestored;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Who hears that an appointment was called off, or that a date takes place again: the members
 * holding a place on the date concerned and whoever looks after them, and nobody else.
 */
class EventCancelledHandlerTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember onTheDate;
    private static StationMember guardian;
    private static StationMember onAnotherDate;
    private static StationMember inThePast;
    private static int eventId;
    private static LocalDate date;

    private NotificationService notificationService;
    private EventCancelledHandler cancelled;
    private EventDateRestoredHandler restored;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("CancelledHandler Station");
        onTheDate = member("cancel-date@test.com");
        guardian = member("cancel-guardian@test.com");
        onAnotherDate = member("cancel-other@test.com");
        inThePast = member("cancel-past@test.com");
        stationMemberRepo.addManager(guardian.id(), onTheDate.id());

        date = LocalDate.now().plusDays(7);
        var event = eventRepo.create(
                station.id(),
                "Cancel Handler Event",
                "desc",
                StationEvent.EventType.RECURRING,
                date.getDayOfWeek().getValue(),
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
        eventRegistrationRepo.create(eventId, onTheDate.id(), date, RegistrationStatus.ACCEPTED, null);
        eventRegistrationRepo.create(eventId, onAnotherDate.id(), date.plusWeeks(1), RegistrationStatus.PENDING, null);
        eventRegistrationRepo.create(
                eventId, inThePast.id(), LocalDate.now().minusWeeks(2), RegistrationStatus.ACCEPTED, null);
    }

    private static StationMember member(String email) {
        Account account = accountRepo.create(email, "Cancel", "Handler");
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        eventRepo.delete(eventId);
        stationRepo.delete(station.id());
    }

    @BeforeEach
    void wire() {
        notificationService = mock(NotificationService.class);
        var guardians = new GuardianPolicy(stationMemberRepo);
        cancelled =
                new EventCancelledHandler(notificationService, eventRegistrationRepo, occurrenceCalendar, guardians);
        restored = new EventDateRestoredHandler(notificationService, eventRegistrationRepo, guardians);
    }

    @Test
    void theyHandleTheirOwnEvents() {
        assertEquals(EventCancelled.class, cancelled.eventType());
        assertEquals(EventDateRestored.class, restored.eventType());
    }

    /** A date called off tells whoever holds a place on it and their guardian, and nobody on another date. */
    @Test
    void aCancelledDateTellsOnlyThatDatesHousehold() {
        cancelled.handle(new EventCancelled(
                station.id(), eventId, "Cancel Handler Event", "Sturm", date, CancellationCause.MANUAL));

        verify(notificationService)
                .notifyMembers(
                        eq(Set.of(onTheDate.id(), guardian.id())),
                        eq(NotificationType.EVENT_CANCELLED),
                        argThat((NotificationData data) ->
                                data.params() instanceof NotificationParams.EventCancelled params
                                        && date.equals(params.eventDate())
                                        && "DATE".equals(params.variant())));
    }

    /** A date the check called off is worded by its cause rather than by a stored reason. */
    @Test
    void aDateCalledOffForTooFewRegistrationsSaysSo() {
        cancelled.handle(new EventCancelled(
                station.id(), eventId, "Cancel Handler Event", null, date, CancellationCause.THRESHOLD));

        verify(notificationService)
                .notifyMembers(
                        any(), eq(NotificationType.EVENT_CANCELLED), argThat((NotificationData data) -> "THRESHOLD"
                                .equals(data.params().variant())));
    }

    /** A whole series tells everybody on a date still to come, and nobody whose date is behind them. */
    @Test
    void aCancelledSeriesTellsEveryPlaceStillToCome() {
        cancelled.handle(EventCancelled.series(station.id(), eventId, "Cancel Handler Event", "Aufgelöst"));

        verify(notificationService)
                .notifyMembers(
                        argThat((Collection<Integer> audience) ->
                                audience.containsAll(Set.of(onTheDate.id(), guardian.id(), onAnotherDate.id()))
                                        && !audience.contains(inThePast.id())),
                        eq(NotificationType.EVENT_CANCELLED),
                        argThat((NotificationData data) -> data.params().variant() == null));
    }

    @Test
    void aDateNobodyHoldsAPlaceOnTellsNobody() {
        cancelled.handle(new EventCancelled(
                station.id(), eventId, "Cancel Handler Event", null, date.plusWeeks(5), CancellationCause.MANUAL));
        restored.handle(new EventDateRestored(station.id(), eventId, "Cancel Handler Event", date.plusWeeks(5)));

        verifyNoInteractions(notificationService);
    }

    /** A date brought back tells whoever kept their place on it, and their guardian. */
    @Test
    void aRestoredDateTellsThatDatesHousehold() {
        restored.handle(new EventDateRestored(station.id(), eventId, "Cancel Handler Event", date));

        verify(notificationService)
                .notifyMembers(
                        eq(Set.of(onTheDate.id(), guardian.id())),
                        eq(NotificationType.EVENT_DATE_RESTORED),
                        argThat((NotificationData data) ->
                                data.params() instanceof NotificationParams.EventDateRestored params
                                        && date.equals(params.eventDate())));
    }
}
