/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.cluster.entity.StationKind;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventDateCancellation;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventBreakRepository;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.DiscoveryVisibility;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.ThemeFeel;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;

class EventReminderCheckerTest {
    private EventRepository eventRepository;
    private EventReminderRepository reminderRepository;
    private EventRegistrationRepository registrationRepository;
    private StationMemberRepository stationMemberRepository;
    private Notifier notifier;
    private MemberNameResolver memberNameResolver;
    private EventRestrictionService restrictionService;
    private StationReadOnlyGuard readOnlyGuard;
    private StationRepository stationRepository;
    private EventBreakRepository breakRepository;
    private EventDateCancellationRepository cancellationRepository;

    private static final int STATION_ID = 1;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        reminderRepository = mock(EventReminderRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        stationMemberRepository = mock(StationMemberRepository.class);
        notifier = mock(Notifier.class);
        memberNameResolver = mock(MemberNameResolver.class);
        restrictionService = mock(EventRestrictionService.class);
        when(restrictionService.canRegister(anyInt(), anyInt(), any())).thenReturn(true);
        when(restrictionService.canView(anyInt(), anyInt(), any())).thenReturn(true);
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        when(readOnlyGuard.isWritable(anyInt())).thenReturn(true);
        stationRepository = mock(StationRepository.class);
        breakRepository = mock(EventBreakRepository.class);
        cancellationRepository = mock(EventDateCancellationRepository.class);
    }

    /**
     * An appointment just after midnight belongs to the day the station is having it on.
     *
     * <p>Half past midnight in Berlin is half past ten the evening before in UTC, so a server
     * reckoning in its own clock puts the appointment on the wrong day and sends the reminder a day
     * early. Which day it falls on, and which day it is now, are both questions about the station's
     * clock, and this is the pair of them.
     */
    @Test
    void anAppointmentJustAfterMidnightIsRemindedOnTheStationsDay() {
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(berlinStation()));
        LocalDate eventDate = LocalDate.now(BERLIN).plusDays(3);
        Instant eventStart = eventDate.atStartOfDay(BERLIN).plusMinutes(30).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(3));
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
        when(registrationRepository.findNotAttendingMemberIds(42, eventDate)).thenReturn(List.of());

        invokeCheck();

        verify(reminderRepository).markSent(42, eventDate, 3);
        verifyReminded(10);
    }

    /** Nobody is reminded of a date that was called off, and the reminder is not spent on it either. */
    @Test
    void aCancelledDateIsNotRemindedOf() {
        when(stationRepository.findById(STATION_ID)).thenReturn(Optional.of(berlinStation()));
        LocalDate eventDate = LocalDate.now(BERLIN).plusDays(3);
        Instant eventStart = eventDate.atTime(18, 0).atZone(BERLIN).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(3));
        when(cancellationRepository.findActiveByStation(STATION_ID))
                .thenReturn(List.of(new EventDateCancellation(
                        42, eventDate, CancellationCause.MANUAL, null, Instant.now(), null, null)));

        invokeCheck();

        verify(reminderRepository, never()).markSent(anyInt(), any(), anyInt());
        verify(notifier, never()).notify(any(), eq(NotificationType.EVENT_REMINDER), any(), any());
    }

    private static Station berlinStation() {
        return new Station(
                STATION_ID,
                null,
                "Test",
                "Europe/Berlin",
                "de-DE",
                null,
                null,
                false,
                null,
                ThemeFeel.ROUNDED,
                false,
                PublicKbMode.OFF,
                DiscoveryVisibility.NONE,
                null,
                false,
                false,
                null,
                false,
                null,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                StationKind.REGULAR,
                null,
                false,
                false);
    }

    private StationEvent oneTimeEvent(int id, Instant startTime, boolean requiresRegistration) {
        return new StationEvent(
                id,
                STATION_ID,
                "Test Event",
                "Description",
                StationEvent.EventType.ONE_TIME,
                null,
                startTime,
                startTime.plusSeconds(3600),
                null,
                requiresRegistration,
                null,
                false,
                null,
                RestrictionMode.OR,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private StationEvent recurringEvent(int id, int dayOfWeek) {
        return new StationEvent(
                id,
                STATION_ID,
                "Weekly Event",
                "Description",
                StationEvent.EventType.RECURRING,
                dayOfWeek,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private StationEvent monthlyFirstEvent(int id, int dayOfWeek) {
        return new StationEvent(
                id,
                STATION_ID,
                "Monthly First Event",
                "Description",
                StationEvent.EventType.MONTHLY_FIRST,
                dayOfWeek,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /** Verifies that exactly these members were reminded of a date. */
    private void verifyReminded(Integer... memberIds) {
        verify(notifier)
                .notify(
                        eq(StationAudience.members(List.of(memberIds))),
                        eq(NotificationType.EVENT_REMINDER),
                        any(NotificationData.class),
                        eq(Delivery.EVERY_TIME));
    }

    private StationMember member(int id) {
        return new StationMember(
                id, STATION_ID, UUID.randomUUID(), id, false, null, "Member " + id, StationUserType.MEMBER, null);
    }

    /**
     * Three days out and one day out, to the member and to whoever answers for them, once each. The sweep
     * runs every half hour, so warning again on the next pass would be the obvious way to get this wrong.
     */
    @Test
    void aClosingRegistrationWarnsWhoeverStillOwesAnAnswer() {
        var closing = new EventRepository.ClosingEvent(7, STATION_ID, "Übung", Instant.now(), 3);
        when(eventRepository.findEventsClosingIn(3)).thenReturn(List.of(closing));
        when(eventRepository.findEventsClosingIn(1)).thenReturn(List.of());
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of());
        when(readOnlyGuard.isWritable(STATION_ID)).thenReturn(true);
        when(registrationRepository.findUnansweredMemberIds(7, STATION_ID)).thenReturn(List.of(10));
        when(memberNameResolver.called(10)).thenReturn("Kind");

        invokeCheck();

        verify(notifier)
                .notify(
                        eq(StationAudience.household(List.of(10))),
                        eq(NotificationType.REGISTRATION_CLOSING),
                        any(NotificationData.class),
                        eq(Delivery.EVERY_TIME));
        verify(reminderRepository).markDeadlineWarningSent(7, 3);
    }

    /**
     * Somebody the appointment is not open to owes no answer, so no deadline is warned about.
     *
     * <p>The list of unanswered members is everybody at the station without a registration, which
     * says nothing about whether they were ever allowed to give one. The warning is still marked as
     * sent, because there is nothing left to warn about on a later pass either.
     */
    @Test
    void nobodyIsWarnedAboutADeadlineTheyCannotMeet() {
        var closing = new EventRepository.ClosingEvent(7, STATION_ID, "Übung", Instant.now(), 3);
        when(eventRepository.findEventsClosingIn(3)).thenReturn(List.of(closing));
        when(eventRepository.findEventsClosingIn(1)).thenReturn(List.of());
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of());
        when(readOnlyGuard.isWritable(STATION_ID)).thenReturn(true);
        when(registrationRepository.findUnansweredMemberIds(7, STATION_ID)).thenReturn(List.of(10));
        when(restrictionService.canRegister(eq(7), eq(10), any())).thenReturn(false);

        invokeCheck();

        verify(notifier, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
        verify(reminderRepository).markDeadlineWarningSent(7, 3);
    }

    /** An event whose warning already went out is passed over rather than warned about again. */
    @Test
    void aWarningAlreadySentIsNotSentAgain() {
        when(eventRepository.findEventsClosingIn(anyInt())).thenReturn(List.of());
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of());

        invokeCheck();

        verify(notifier, never()).notify(any(), eq(NotificationType.REGISTRATION_CLOSING), any(), any());
    }

    @Test
    void checkSendsReminderForOneTimeEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(3);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(3));
        when(reminderRepository.isSent(42, eventDate, 3)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10), member(11)));
        when(registrationRepository.findNotAttendingMemberIds(42, eventDate)).thenReturn(List.of());

        createCheckerWithoutScheduler();

        // The check runs after 5 minutes delay via scheduler, so we invoke it indirectly via constructor.
        // Instead, we test the logic by calling the method reflectively.
        try {
            var method = EventReminderChecker.class.getDeclaredMethod("check");
            method.setAccessible(true);
            var checker = createCheckerWithoutScheduler();
            method.invoke(checker);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        verifyReminded(10, 11);
        verify(reminderRepository).markSent(42, eventDate, 3);
    }

    @Test
    void checkSkipsAlreadySentReminder() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(1);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));
        when(reminderRepository.isSent(42, eventDate, 1)).thenReturn(true);

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
        verify(reminderRepository, never()).markSent(anyInt(), any(), anyInt());
    }

    @Test
    void checkUsesRegisteredMembersWhenRegistrationRequired() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(2);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, true);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(2));
        when(reminderRepository.isSent(42, eventDate, 2)).thenReturn(false);
        when(registrationRepository.findRegisteredMemberIds(42, eventDate)).thenReturn(List.of(20, 21));

        invokeCheck();

        verifyReminded(20, 21);
        verify(stationMemberRepository, never()).findByStation(anyInt());
    }

    @Test
    void checkSkipsWhenNoTargetMembers() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(1);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, true);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));
        when(reminderRepository.isSent(42, eventDate, 1)).thenReturn(false);
        when(registrationRepository.findRegisteredMemberIds(42, eventDate)).thenReturn(List.of());

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
        verify(reminderRepository).markSent(42, eventDate, 1);
    }

    @Test
    void checkHandlesRecurringEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int todayDow = today.getDayOfWeek().getValue();

        var event = recurringEvent(50, todayDow);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(50)).thenReturn(List.of(0));
        when(reminderRepository.isSent(50, today, 0)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
        when(registrationRepository.findNotAttendingMemberIds(50, today)).thenReturn(List.of());

        invokeCheck();

        verifyReminded(10);
    }

    @Test
    void checkExcludesDeclinedMembers() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(1);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));
        when(reminderRepository.isSent(42, eventDate, 1)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10), member(11)));
        when(registrationRepository.findNotAttendingMemberIds(42, eventDate)).thenReturn(List.of(11));

        invokeCheck();

        verifyReminded(10);
    }

    @Test
    void checkHandlesExceptionGracefully() {
        when(eventRepository.findEventsWithReminders()).thenThrow(new RuntimeException("DB down"));

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void checkSkipsPastOneTimeEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.minusDays(1);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    @Test
    void checkHandlesNullStartTime() {
        var event = new StationEvent(
                42,
                STATION_ID,
                "No Start",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
    }

    /**
     * A day the station takes a break on is not an occurrence, so nobody is reminded of it.
     *
     * <p>The reminders used to read the repetition alone, and members were told to come to a drill
     * the station had called off for the holidays.
     */
    @Test
    void nobodyIsRemindedOfADateABreakTakesOut() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var event = recurringEvent(50, today.getDayOfWeek().getValue());
        when(breakRepository.findByStation(STATION_ID))
                .thenReturn(List.of(new EventBreak(1, STATION_ID, "Ferien", today, today)));
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(50)).thenReturn(List.of(0));
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));

        invokeCheck();

        verify(notifier, never()).notify(any(), any(), any(), any());
        verify(reminderRepository, never()).markSent(anyInt(), any(), anyInt());
    }

    /** A weekly series that names no weekday repeats on the weekday of its start, as a calendar reads it. */
    @Test
    void aWeeklySeriesWithoutAWeekdayIsRemindedOnTheWeekdayOfItsStart() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var event = new StationEvent(
                42,
                STATION_ID,
                "Bad Recurring",
                "desc",
                StationEvent.EventType.RECURRING,
                null,
                Instant.now(),
                Instant.now().plusSeconds(3600),
                null,
                false,
                null,
                false,
                null,
                RestrictionMode.OR,
                RestrictionMode.AND,
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(0));
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
        when(registrationRepository.findNotAttendingMemberIds(42, today)).thenReturn(List.of());

        invokeCheck();

        verify(reminderRepository).markSent(42, today, 0);
    }

    @Test
    void checkHandlesMonthlyFirstEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        // Find a date within the first 7 days of this month matching today's DOW
        LocalDate firstOfMonth = today.withDayOfMonth(1);
        LocalDate target = firstOfMonth;
        while (target.getDayOfWeek().getValue() != today.getDayOfWeek().getValue()) {
            target = target.plusDays(1);
        }
        // Only test if that target matches today (within first 7 days)
        if (target.equals(today) && today.getDayOfMonth() <= 7) {
            var event = monthlyFirstEvent(60, today.getDayOfWeek().getValue());
            when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
            when(reminderRepository.findDays(60)).thenReturn(List.of(0));
            when(reminderRepository.isSent(60, today, 0)).thenReturn(false);
            when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
            when(registrationRepository.findNotAttendingMemberIds(60, today)).thenReturn(List.of());

            invokeCheck();

            verifyReminded(10);
        }
    }

    private EventReminderChecker createCheckerWithoutScheduler() {
        try {
            var ctor = EventReminderChecker.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (EventReminderChecker) ctor.newInstance(
                    eventRepository,
                    reminderRepository,
                    registrationRepository,
                    stationMemberRepository,
                    notifier,
                    memberNameResolver,
                    restrictionService,
                    readOnlyGuard,
                    new OccurrenceCalendar(
                            eventRepository, breakRepository, cancellationRepository, stationRepository));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invokeCheck() {
        try {
            var checker = createCheckerWithoutScheduler();
            var method = EventReminderChecker.class.getDeclaredMethod("check");
            method.setAccessible(true);
            method.invoke(checker);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
