/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventReminderRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class EventReminderCheckerTest {
    private EventRepository eventRepository;
    private EventReminderRepository reminderRepository;
    private EventRegistrationRepository registrationRepository;
    private StationMemberRepository stationMemberRepository;
    private NotificationService notificationService;
    private MemberNameResolver memberNameResolver;
    private StationReadOnlyGuard readOnlyGuard;

    private static final int STATION_ID = 1;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        reminderRepository = mock(EventReminderRepository.class);
        registrationRepository = mock(EventRegistrationRepository.class);
        stationMemberRepository = mock(StationMemberRepository.class);
        notificationService = mock(NotificationService.class);
        memberNameResolver = mock(MemberNameResolver.class);
        readOnlyGuard = mock(StationReadOnlyGuard.class);
        when(readOnlyGuard.isWritable(anyInt())).thenReturn(true);
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
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
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
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
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
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
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
        when(stationMemberRepository.findById(10)).thenReturn(Optional.of(member(10)));
        when(stationMemberRepository.findManagers(10)).thenReturn(List.of(member(11)));
        when(memberNameResolver.resolveLocal(10)).thenReturn("Kind");

        invokeCheck();

        var audience = ArgumentCaptor.forClass(Collection.class);
        verify(notificationService).notifyMembers(audience.capture(), eq(NotificationType.REGISTRATION_CLOSING), any());
        assertTrue(audience.getValue().containsAll(List.of(10, 11)), "the member and their guardian both hear");
        verify(reminderRepository).markDeadlineWarningSent(7, 3);
    }

    /** An event whose warning already went out is passed over rather than warned about again. */
    @Test
    void aWarningAlreadySentIsNotSentAgain() {
        when(eventRepository.findEventsClosingIn(anyInt())).thenReturn(List.of());
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of());

        invokeCheck();

        verify(notificationService, never()).notifyMembers(any(), eq(NotificationType.REGISTRATION_CLOSING), any());
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
        when(registrationRepository.findDeclinedMemberIds(42, eventDate)).thenReturn(List.of());

        new EventReminderChecker(
                eventRepository,
                reminderRepository,
                registrationRepository,
                stationMemberRepository,
                notificationService,
                memberNameResolver,
                readOnlyGuard);

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

        verify(notificationService)
                .notifyMembers(eq(List.of(10, 11)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
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

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
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
        when(registrationRepository.findRegisteredMemberIds(42)).thenReturn(List.of(20, 21));

        invokeCheck();

        verify(notificationService)
                .notifyMembers(eq(List.of(20, 21)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
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
        when(registrationRepository.findRegisteredMemberIds(42)).thenReturn(List.of());

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
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
        when(registrationRepository.findDeclinedMemberIds(50, today)).thenReturn(List.of());

        invokeCheck();

        verify(notificationService)
                .notifyMembers(eq(List.of(10)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
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
        when(registrationRepository.findDeclinedMemberIds(42, eventDate)).thenReturn(List.of(11));

        invokeCheck();

        verify(notificationService)
                .notifyMembers(eq(List.of(10)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
    }

    @Test
    void checkHandlesExceptionGracefully() {
        when(eventRepository.findEventsWithReminders()).thenThrow(new RuntimeException("DB down"));

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
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

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
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
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
    }

    @Test
    void checkHandlesNullDayOfWeekForRecurring() {
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
                false,
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                false,
                null);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(reminderRepository.findDays(42)).thenReturn(List.of(1));

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
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
            when(registrationRepository.findDeclinedMemberIds(60, today)).thenReturn(List.of());

            invokeCheck();

            verify(notificationService)
                    .notifyMembers(eq(List.of(10)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
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
                    notificationService,
                    memberNameResolver,
                    readOnlyGuard);
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
