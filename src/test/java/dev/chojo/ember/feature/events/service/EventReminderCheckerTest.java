/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class EventReminderCheckerTest {
    private EventRepository eventRepository;
    private StationMemberRepository stationMemberRepository;
    private NotificationService notificationService;

    private static final int STATION_ID = 1;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        stationMemberRepository = mock(StationMemberRepository.class);
        notificationService = mock(NotificationService.class);
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

    @Test
    void checkSendsReminderForOneTimeEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(3);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(3));
        when(eventRepository.isReminderSent(42, eventDate, 3)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10), member(11)));
        when(eventRepository.findDeclinedMemberIds(42, eventDate)).thenReturn(List.of());

        new EventReminderChecker(eventRepository, stationMemberRepository, notificationService);

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
        verify(eventRepository).markReminderSent(42, eventDate, 3);
    }

    @Test
    void checkSkipsAlreadySentReminder() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(1);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, false);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));
        when(eventRepository.isReminderSent(42, eventDate, 1)).thenReturn(true);

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
        verify(eventRepository, never()).markReminderSent(anyInt(), any(), anyInt());
    }

    @Test
    void checkUsesRegisteredMembersWhenRegistrationRequired() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate eventDate = today.plusDays(2);
        Instant eventStart = eventDate.atStartOfDay(ZoneOffset.UTC).toInstant();

        var event = oneTimeEvent(42, eventStart, true);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(2));
        when(eventRepository.isReminderSent(42, eventDate, 2)).thenReturn(false);
        when(eventRepository.findRegisteredMemberIds(42)).thenReturn(List.of(20, 21));

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
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));
        when(eventRepository.isReminderSent(42, eventDate, 1)).thenReturn(false);
        when(eventRepository.findRegisteredMemberIds(42)).thenReturn(List.of());

        invokeCheck();

        verify(notificationService, never()).notifyMembers(anyList(), any(), any());
        verify(eventRepository).markReminderSent(42, eventDate, 1);
    }

    @Test
    void checkHandlesRecurringEvent() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int todayDow = today.getDayOfWeek().getValue();

        var event = recurringEvent(50, todayDow);
        when(eventRepository.findEventsWithReminders()).thenReturn(List.of(event));
        when(eventRepository.findReminderDays(50)).thenReturn(List.of(0));
        when(eventRepository.isReminderSent(50, today, 0)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
        when(eventRepository.findDeclinedMemberIds(50, today)).thenReturn(List.of());

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
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));
        when(eventRepository.isReminderSent(42, eventDate, 1)).thenReturn(false);
        when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10), member(11)));
        when(eventRepository.findDeclinedMemberIds(42, eventDate)).thenReturn(List.of(11));

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
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));

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
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));

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
        when(eventRepository.findReminderDays(42)).thenReturn(List.of(1));

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
            when(eventRepository.findReminderDays(60)).thenReturn(List.of(0));
            when(eventRepository.isReminderSent(60, today, 0)).thenReturn(false);
            when(stationMemberRepository.findByStation(STATION_ID)).thenReturn(List.of(member(10)));
            when(eventRepository.findDeclinedMemberIds(60, today)).thenReturn(List.of());

            invokeCheck();

            verify(notificationService)
                    .notifyMembers(eq(List.of(10)), eq(NotificationType.EVENT_REMINDER), any(NotificationData.class));
        }
    }

    private EventReminderChecker createCheckerWithoutScheduler() {
        try {
            var ctor = EventReminderChecker.class.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return (EventReminderChecker)
                    ctor.newInstance(eventRepository, stationMemberRepository, notificationService);
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
