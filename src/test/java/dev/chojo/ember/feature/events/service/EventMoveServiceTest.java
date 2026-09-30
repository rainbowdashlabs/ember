/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.handlers.EventChangedHandler;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * An appointment moved while people are signed up for it, walked through the same path an edit
 * takes: the change is saved, announced, and followed.
 */
class EventMoveServiceTest extends RepositoryTestBase {

    private static Station station;
    private static StationMember child;
    private static StationMember other;
    private static final List<Integer> accounts = new ArrayList<>();

    private Notifier notifications;
    private EventCrudService crud;
    private LocalDate today;
    private ZoneId zone;
    private StationEvent event;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Moved Appointment Station");
        child = memberOf("child@moved.test", "Carla");
        other = memberOf("other@moved.test", "Otto");
    }

    private static StationMember memberOf(String email, String firstName) {
        var account = accountRepo.create(email, firstName, "Moved");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    @BeforeEach
    void wire() {
        notifications = mock(Notifier.class);
        var moveService = new EventMoveService(
                eventRegistrationRepo,
                eventFieldRepo,
                eventReminderRepo,
                eventDateCancellationRepo,
                occurrenceCalendar,
                memberNameResolver,
                notifications);
        crud = newEventServices(new DomainEventBus(Set.of(new EventChangedHandler(() -> moveService))))
                .crud();
        var calendar = occurrenceCalendar.forStation(station.id());
        today = calendar.today();
        zone = calendar.zone();
    }

    @AfterEach
    void removeEvent() {
        if (event != null) eventRepo.delete(event.id());
        event = null;
    }

    /** A weekly series on this weekday at six in the evening, started a month ago. */
    private StationEvent weeklyOn(DayOfWeek day) {
        LocalDate first = today.minusWeeks(4).with(TemporalAdjusters.nextOrSame(day));
        Instant start = first.atTime(18, 0).atZone(zone).toInstant();
        return create(StationEvent.EventType.RECURRING, day.getValue(), start);
    }

    /** A one-off appointment on this day at six in the evening. */
    private StationEvent oneOffOn(LocalDate day) {
        return create(
                StationEvent.EventType.ONE_TIME,
                null,
                day.atTime(18, 0).atZone(zone).toInstant());
    }

    private StationEvent create(StationEvent.EventType type, Integer dayOfWeek, Instant start) {
        event = crud.create(
                station.id(),
                "Dienst",
                "",
                type,
                dayOfWeek,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        return event;
    }

    /** Saves the appointment with a new name, weekday and times, the way the edit screen does. */
    private StationEvent change(StationEvent current, String name, Integer dayOfWeek, Instant start) {
        return crud.update(
                        current.id(),
                        name,
                        current.description(),
                        current.eventType(),
                        dayOfWeek,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        current.templateId(),
                        current.requiresRegistration(),
                        current.registrationDeadline(),
                        current.requiresConfirmation(),
                        current.categoryId(),
                        current.isPublic(),
                        current.registrationLimit(),
                        current.minRegistrations(),
                        current.thresholdDays(),
                        current.registrationCloseDays())
                .orElseThrow();
    }

    private StationEvent moveTo(StationEvent current, DayOfWeek day) {
        return change(current, current.name(), day.getValue(), current.startTime());
    }

    private EventRegistration register(StationMember member, LocalDate date, RegistrationStatus status) {
        return eventRegistrationRepo.create(event.id(), member.id(), date, status, null);
    }

    private EventRegistration reread(EventRegistration registration) {
        return eventRegistrationRepo.findById(registration.id()).orElseThrow();
    }

    private LocalDate nextTuesday() {
        return today.with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    }

    /**
     * That this member's household, the member and whoever looks after them, was told a date went
     * away, and which next date it was offered.
     */
    private void verifyToldDropped(StationMember member, LocalDate date, LocalDate nextDate) {
        verify(notifications)
                .notify(
                        eq(StationAudience.household(List.of(member.id()))),
                        eq(NotificationType.EVENT_DATE_DROPPED),
                        argThat((NotificationData data) ->
                                data.params() instanceof NotificationParams.EventDateDropped dropped
                                        && dropped.eventDate().equals(date)
                                        && Objects.equals(dropped.nextDate(), nextDate)),
                        eq(Delivery.EVERY_TIME));
    }

    /**
     * A weekly Tuesday moved to Wednesdays gives back every Tuesday place still ahead, pending or
     * confirmed, and tells the member and whoever looks after them, naming the Wednesday that follows.
     * A Tuesday somebody had already said no to stays a no.
     */
    @Test
    void aSeriesMovedToAnotherWeekdayWithdrawsTheOldDaysAndTellsTheHousehold() {
        weeklyOn(DayOfWeek.TUESDAY);
        var confirmed = register(child, nextTuesday(), RegistrationStatus.ACCEPTED);
        var pending = register(other, nextTuesday().plusWeeks(1), RegistrationStatus.PENDING);
        var declined = register(other, nextTuesday(), RegistrationStatus.DECLINED);

        moveTo(event, DayOfWeek.WEDNESDAY);

        assertEquals(RegistrationStatus.WITHDRAWN, reread(confirmed).status());
        assertEquals(RegistrationStatus.WITHDRAWN, reread(pending).status());
        assertEquals(RegistrationStatus.DECLINED, reread(declined).status());
        assertNull(reread(confirmed).previousStatus(), "a place the day took away is not the member's to undo");
        verifyToldDropped(child, nextTuesday(), nextTuesday().plusDays(1));
        verifyToldDropped(
                other, nextTuesday().plusWeeks(1), nextTuesday().plusWeeks(1).plusDays(1));
    }

    /** A date the appointment still falls on keeps its places, and nobody hears anything. */
    @Test
    void aNewTimeOnTheSameDaysLeavesEveryPlaceAlone() {
        weeklyOn(DayOfWeek.TUESDAY);
        var confirmed = register(child, nextTuesday(), RegistrationStatus.ACCEPTED);

        change(event, event.name(), event.dayOfWeek(), event.startTime().plus(1, ChronoUnit.HOURS));

        assertEquals(RegistrationStatus.ACCEPTED, reread(confirmed).status());
        verifyNoInteractions(notifications);
    }

    /**
     * A series told to end sooner withdraws the places after its new last day and keeps the ones up
     * to it. Nothing follows the dates it gave up, and the message says so.
     */
    @Test
    void aSeriesEndingSoonerWithdrawsTheDatesAfterItsNewEnd() {
        weeklyOn(DayOfWeek.TUESDAY);
        var kept = register(child, nextTuesday(), RegistrationStatus.ACCEPTED);
        var dropped = register(child, nextTuesday().plusWeeks(3), RegistrationStatus.ACCEPTED);

        crud.setRepeatEnd(event.id(), nextTuesday().plusWeeks(1), null);

        assertEquals(RegistrationStatus.ACCEPTED, reread(kept).status());
        assertEquals(RegistrationStatus.WITHDRAWN, reread(dropped).status());
        verifyToldDropped(child, nextTuesday().plusWeeks(3), null);
    }

    /**
     * A one-off appointment moved to another day is the same occasion: the place goes with it,
     * confirmed as it was, and so does what the question asked of that day. Everybody holding a
     * place is told when it now starts.
     */
    @Test
    void aOneOffAppointmentMovedKeepsItsPlacesAndTellsTheNewTime() {
        LocalDate day = today.plusDays(3);
        LocalDate newDay = today.plusDays(5);
        oneOffOn(day);
        var confirmed = register(child, day, RegistrationStatus.ACCEPTED);
        var field = perDateQuestion();
        eventFieldRepo.updateValueOn(field, day, "Bus");

        change(event, event.name(), null, newDay.atTime(10, 0).atZone(zone).toInstant());

        var moved = reread(confirmed);
        assertEquals(newDay, moved.eventDate());
        assertEquals(RegistrationStatus.ACCEPTED, moved.status());
        assertEquals("Bus", eventFieldRepo.findDateValues(event.id()).get(field).get(newDay));
        verify(notifications)
                .notify(
                        eq(StationAudience.household(List.of(child.id()))),
                        eq(NotificationType.EVENT_MOVED),
                        argThat((NotificationData data) -> data.params() instanceof NotificationParams.EventMoved m
                                && m.eventDate().equals(newDay)
                                && m.startTime().equals("10:00")),
                        eq(Delivery.EVERY_TIME));
        verify(notifications, never()).notify(any(), eq(NotificationType.EVENT_DATE_DROPPED), any(), any());
    }

    /** A date that was called off is still a date of the series, so a change of time keeps its places. */
    @Test
    void aCancelledDateKeepsItsPlacesWhenTheSeriesChanges() {
        weeklyOn(DayOfWeek.TUESDAY);
        var kept = register(child, nextTuesday(), RegistrationStatus.ACCEPTED);
        eventDateCancellationRepo.cancel(event.id(), nextTuesday(), CancellationCause.MANUAL, null, null);

        change(event, event.name(), event.dayOfWeek(), event.startTime().plus(1, ChronoUnit.HOURS));

        assertEquals(RegistrationStatus.ACCEPTED, reread(kept).status());
        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), nextTuesday()));
    }

    /** A one-off that was called off stays called off on the day it moved to. */
    @Test
    void aMovedOneOffTakesItsCancellationAlong() {
        LocalDate day = today.plusDays(3);
        LocalDate newDay = today.plusDays(5);
        oneOffOn(day);
        eventDateCancellationRepo.cancel(event.id(), day, CancellationCause.MANUAL, "Sturm", null);

        change(event, event.name(), null, newDay.atTime(10, 0).atZone(zone).toInstant());

        assertTrue(eventDateCancellationRepo.isCancelled(event.id(), newDay));
        assertFalse(eventDateCancellationRepo.isCancelled(event.id(), day));
    }

    /** A date the series no longer falls on takes what said it was off with it. */
    @Test
    void theCancellationOfADroppedDateIsForgotten() {
        weeklyOn(DayOfWeek.TUESDAY);
        register(child, nextTuesday(), RegistrationStatus.ACCEPTED);
        eventDateCancellationRepo.cancel(event.id(), nextTuesday(), CancellationCause.MANUAL, null, null);

        moveTo(event, DayOfWeek.WEDNESDAY);

        assertTrue(eventDateCancellationRepo.find(event.id(), nextTuesday()).isEmpty());
    }

    /**
     * The record that a reminder went out goes with the date it was for, and a reminder for a day
     * already gone by is history and stays.
     */
    @Test
    void theRemindersOfADroppedDateAreForgotten() {
        weeklyOn(DayOfWeek.TUESDAY);
        LocalDate lastTuesday = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
        eventReminderRepo.markSent(event.id(), nextTuesday(), 1);
        eventReminderRepo.markSent(event.id(), lastTuesday, 1);

        moveTo(event, DayOfWeek.WEDNESDAY);

        assertFalse(eventReminderRepo.isSent(event.id(), nextTuesday(), 1));
        assertTrue(eventReminderRepo.isSent(event.id(), lastTuesday, 1));
    }

    /**
     * A place a question gave on a dropped date is withdrawn with the others, and the answer naming
     * the member for that day goes, so comparing the questions again does not bring it back.
     */
    @Test
    void aPlaceAQuestionGaveOnADroppedDateStaysWithdrawn() {
        weeklyOn(DayOfWeek.TUESDAY);
        var field = perDateQuestion();
        eventFieldRepo.updateValueOn(field, nextTuesday(), QuestionValues.formatMembers(List.of(child.id())));
        eventFieldRegistrationService.reconcile(event.id());

        moveTo(event, DayOfWeek.WEDNESDAY);
        eventFieldRegistrationService.reconcile(event.id());

        var registration = eventRegistrationRepo
                .findByEventAndDate(event.id(), nextTuesday())
                .getFirst();
        assertEquals(RegistrationStatus.WITHDRAWN, registration.status());
        assertFalse(registration.fromField());
        assertFalse(eventFieldRepo
                .findDateValues(event.id())
                .getOrDefault(field, Map.of())
                .containsKey(nextTuesday()));
    }

    /** A new name moves no date, so nothing filed against one changes and nobody is told. */
    @Test
    void aRenameDoesNothing() {
        weeklyOn(DayOfWeek.TUESDAY);
        var confirmed = register(child, nextTuesday(), RegistrationStatus.ACCEPTED);

        change(event, "Übungsdienst", event.dayOfWeek(), event.startTime());

        assertEquals(RegistrationStatus.ACCEPTED, reread(confirmed).status());
        verifyNoInteractions(notifications);
    }

    /** A date before today is what happened, and moving the series does not rewrite it. */
    @Test
    void aPastRegistrationIsNeverTouched() {
        weeklyOn(DayOfWeek.TUESDAY);
        LocalDate lastTuesday = today.minusDays(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.TUESDAY));
        var past = register(child, lastTuesday, RegistrationStatus.ACCEPTED);

        moveTo(event, DayOfWeek.WEDNESDAY);

        assertEquals(RegistrationStatus.ACCEPTED, reread(past).status());
        verify(notifications, never()).notify(any(), any(), any(), any());
    }

    /** A question naming members that is answered per date, returning its id. */
    private int perDateQuestion() {
        return eventFieldRepo
                .create(
                        event.id(),
                        "Fahrer",
                        EventFieldType.MEMBER_LIST,
                        new EventFieldConfig(null, null, null, null, true, null, true),
                        "",
                        0,
                        false,
                        null,
                        false)
                .id();
    }
}
