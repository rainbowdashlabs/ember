/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.RegistrationDeadlineSql;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sign-up deadline tells the managers about whoever waits for a decision, never about whoever waits
 * for a place, and decides nothing for either.
 */
class WaitingThroughTheDeadlineTest extends RepositoryTestBase {
    private static Station station;
    private static Account pendingAccount;
    private static Account waitingAccount;
    private static StationMember pending;
    private static StationMember waiting;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Waiting Through Deadline Station");
        pendingAccount = accountRepo.create("deadline-pending@test.com", "Offen", "Ist");
        waitingAccount = accountRepo.create("deadline-waiting@test.com", "Warte", "Nd");
        pending = stationMemberRepo.create(station.id(), pendingAccount.id());
        waiting = stationMemberRepo.create(station.id(), waitingAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(pendingAccount.id());
        accountRepo.delete(waitingAccount.id());
    }

    @Test
    void theDeadlineCountsWhoWaitsForADecisionAndLeavesEverySignUpAsItIs() {
        var start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var zone = occurrenceCalendar.zoneOf(station.id());
        var event = eventRepo.create(
                station.id(),
                "Wochendienst",
                "desc",
                StationEvent.EventType.RECURRING,
                start.atZone(zone).getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.relative(43200, RegistrationDeadline.DeadlineAnchor.MIDNIGHT),
                true,
                null,
                1,
                null,
                null);
        try {
            var date = start.atZone(zone).toLocalDate();
            var asked = eventRegistrationRepo.create(event.id(), pending.id(), date, RegistrationStatus.PENDING, null);
            var inLine = eventRegistrationRepo.create(event.id(), waiting.id(), date, RegistrationStatus.QUEUED, null);
            var guard = mock(StationReadOnlyGuard.class);
            when(guard.isWritable(anyInt())).thenReturn(true);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, guard);

            checker.scheduledTasks().getFirst().work().run();

            verify(bus)
                    .publish(new RegistrationDeadlineExpired(
                            station.id(), event.name(), 1, date, NotificationLinks.eventRegistrations(event.id())));
            assertEquals(
                    RegistrationStatus.PENDING,
                    eventRegistrationRepo.findById(asked.id()).orElseThrow().status());
            assertEquals(
                    RegistrationStatus.QUEUED,
                    eventRegistrationRepo.findById(inLine.id()).orElseThrow().status());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /**
     * A relative deadline closes each date of a series on its own: the date whose deadline passed is
     * told about once, the next date waits for its own deadline, and every sign-up stays waiting.
     */
    @Test
    void aSeriesIsToldAboutEachDateWhoseRelativeDeadlinePassedOnce() {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var near = LocalDate.now(zone).plusDays(1);
        var next = near.plusWeeks(1);
        var event = series(near, RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
        try {
            var nearSignUp =
                    eventRegistrationRepo.create(event.id(), pending.id(), near, RegistrationStatus.PENDING, null);
            var nextSignUp =
                    eventRegistrationRepo.create(event.id(), pending.id(), next, RegistrationStatus.PENDING, null);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, writable());

            checker.scheduledTasks().getFirst().work().run();
            checker.scheduledTasks().getFirst().work().run();

            verify(bus, times(1)).publish(any(RegistrationDeadlineExpired.class));
            verify(bus)
                    .publish(new RegistrationDeadlineExpired(
                            station.id(), event.name(), 1, near, NotificationLinks.eventRegistrations(event.id())));
            assertEquals(
                    RegistrationStatus.PENDING,
                    eventRegistrationRepo
                            .findById(nearSignUp.id())
                            .orElseThrow()
                            .status());
            assertEquals(
                    RegistrationStatus.PENDING,
                    eventRegistrationRepo
                            .findById(nextSignUp.id())
                            .orElseThrow()
                            .status());
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /**
     * A fixed deadline closes every date of a series at once, so the managers get one notice counting
     * the waiting sign-ups of all its dates.
     */
    @Test
    void aFixedDeadlineOnASeriesIsToldAboutOnceWithEveryDateCounted() {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var near = LocalDate.now(zone).plusDays(1);
        var event = series(near, RegistrationDeadline.fixed(Instant.now().minus(1, ChronoUnit.HOURS)));
        try {
            eventRegistrationRepo.create(event.id(), pending.id(), near, RegistrationStatus.PENDING, null);
            eventRegistrationRepo.create(event.id(), pending.id(), near.plusWeeks(1), RegistrationStatus.PENDING, null);
            eventRegistrationRepo.create(event.id(), waiting.id(), near.plusWeeks(1), RegistrationStatus.PENDING, null);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, writable());

            checker.scheduledTasks().getFirst().work().run();
            checker.scheduledTasks().getFirst().work().run();

            verify(bus, times(1)).publish(any(RegistrationDeadlineExpired.class));
            verify(bus)
                    .publish(new RegistrationDeadlineExpired(
                            station.id(), event.name(), 3, null, NotificationLinks.eventRegistrations(event.id())));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /** A date called off closes nothing: its passed deadline is not told about while it stays off. */
    @Test
    void aCalledOffDateIsNotToldAbout() {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var near = LocalDate.now(zone).plusDays(1);
        var event = series(near, RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
        try {
            eventRegistrationRepo.create(event.id(), pending.id(), near, RegistrationStatus.PENDING, null);
            eventDateCancellationRepo.cancel(event.id(), near, CancellationCause.MANUAL, null, null);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, writable());

            checker.scheduledTasks().getFirst().work().run();

            verify(bus, never()).publish(any(RegistrationDeadlineExpired.class));
            assertTrue(
                    eventRepo.findPendingDatesWithDeadline().stream()
                            .anyMatch(date ->
                                    date.eventId() == event.id() && date.date().equals(near)),
                    "the date is asked again should it be brought back");
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /**
     * A notice counts for the close it was about: an unchanged deadline is told about once, one moved
     * to another instant that has passed as well is told about again, without anything arming it.
     */
    @Test
    void aMovedDeadlineOfAOneTimeAppointmentIsToldAboutAgain() {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var day = LocalDate.now(zone).plusDays(2);
        var start = day.atTime(18, 0).atZone(zone).toInstant();
        var closes = Instant.now().minus(3, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        var event = eventRepo.create(
                station.id(),
                "Einmalig",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                RegistrationDeadline.fixed(closes),
                true,
                null,
                null,
                null,
                null);
        try {
            eventRegistrationRepo.create(event.id(), pending.id(), day, RegistrationStatus.PENDING, null);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, writable());

            checker.scheduledTasks().getFirst().work().run();
            checker.scheduledTasks().getFirst().work().run();
            verify(bus, times(1)).publish(any(RegistrationDeadlineExpired.class));

            setDeadline(event, RegistrationDeadline.fixed(closes.plus(1, ChronoUnit.HOURS)));
            checker.scheduledTasks().getFirst().work().run();
            checker.scheduledTasks().getFirst().work().run();

            verify(bus, times(2)).publish(any(RegistrationDeadlineExpired.class));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    /** The same per date of a series: a relative deadline made longer closes the date again. */
    @Test
    void aMovedDeadlineOfASeriesDateIsToldAboutAgain() {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var near = LocalDate.now(zone).plusDays(1);
        var event = series(near, RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
        try {
            eventRegistrationRepo.create(event.id(), pending.id(), near, RegistrationStatus.PENDING, null);
            var bus = mock(DomainEventBus.class);
            var checker = new RegistrationDeadlineChecker(eventRepo, bus, occurrenceCalendar, writable());

            checker.scheduledTasks().getFirst().work().run();
            setDeadline(event, RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
            checker.scheduledTasks().getFirst().work().run();
            verify(bus, times(1)).publish(any(RegistrationDeadlineExpired.class));

            setDeadline(event, RegistrationDeadline.relative(4 * 24 * 60, RegistrationDeadline.DeadlineAnchor.EXACT));
            checker.scheduledTasks().getFirst().work().run();

            verify(bus, times(2)).publish(any(RegistrationDeadlineExpired.class));
            verify(bus, times(2))
                    .publish(new RegistrationDeadlineExpired(
                            station.id(), event.name(), 1, near, NotificationLinks.eventRegistrations(event.id())));
        } finally {
            eventRepo.delete(event.id());
        }
    }

    private static void setDeadline(StationEvent event, RegistrationDeadline deadline) {
        query("UPDATE station_event SET %s WHERE id = :id;".formatted(RegistrationDeadlineSql.SET))
                .single(RegistrationDeadlineSql.bind(call(), deadline).bind("id", event.id()))
                .update();
    }

    private StationEvent series(LocalDate first, RegistrationDeadline deadline) {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var start = first.atTime(18, 0).atZone(zone).toInstant();
        return eventRepo.create(
                station.id(),
                "Wochenübung",
                "desc",
                StationEvent.EventType.RECURRING,
                first.getDayOfWeek().getValue(),
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                deadline,
                true,
                null,
                null,
                null,
                null);
    }

    private static StationReadOnlyGuard writable() {
        var guard = mock(StationReadOnlyGuard.class);
        when(guard.isWritable(anyInt())).thenReturn(true);
        return guard;
    }
}
