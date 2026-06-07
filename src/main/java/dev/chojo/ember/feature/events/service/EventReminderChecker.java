/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Singleton
public class EventReminderChecker {
    private static final Logger log = LoggerFactory.getLogger(EventReminderChecker.class);

    private final EventRepository eventRepository;
    private final StationMemberRepository stationMemberRepository;
    private final NotificationService notificationService;

    @Inject
    public EventReminderChecker(
            EventRepository eventRepository,
            StationMemberRepository stationMemberRepository,
            NotificationService notificationService) {
        this.eventRepository = eventRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.notificationService = notificationService;
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "event-reminder-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 5, 30, TimeUnit.MINUTES);
    }

    private void check() {
        try {
            var events = eventRepository.findEventsWithReminders();
            LocalDate today = LocalDate.now(ZoneOffset.UTC);

            for (var event : events) {
                var reminderDays = eventRepository.findReminderDays(event.id());
                var occurrences = computeOccurrences(event, today, reminderDays);

                for (var occurrence : occurrences) {
                    for (int daysBefore : reminderDays) {
                        LocalDate reminderDate = occurrence.minusDays(daysBefore);
                        if (!today.equals(reminderDate)) continue;
                        if (eventRepository.isReminderSent(event.id(), occurrence, daysBefore)) continue;

                        var targetIds = resolveTargetMembers(event, occurrence);
                        if (!targetIds.isEmpty()) {
                            String dateStr = occurrence.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                            notificationService.notifyMembers(
                                    targetIds,
                                    NotificationType.EVENT_REMINDER,
                                    NotificationData.of(
                                            new NotificationParams.EventReminder(event.name(), daysBefore, dateStr),
                                            new NotificationData.NotificationLink(
                                                    "event-detail", Map.of("id", String.valueOf(event.id())))));
                            log.info(
                                    "Sent {} reminder(s) for event '{}' (id={}) on {} — {} days before",
                                    targetIds.size(),
                                    event.name(),
                                    event.id(),
                                    dateStr,
                                    daysBefore);
                        }
                        eventRepository.markReminderSent(event.id(), occurrence, daysBefore);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error checking event reminders", e);
        }
    }

    private List<LocalDate> computeOccurrences(StationEvent event, LocalDate today, List<Integer> reminderDays) {
        int maxDays = reminderDays.stream().mapToInt(Integer::intValue).max().orElse(0);
        var result = new java.util.ArrayList<LocalDate>();

        if (event.eventType() == StationEvent.EventType.ONE_TIME) {
            if (event.startTime() != null) {
                LocalDate eventDate = event.startTime().atZone(ZoneOffset.UTC).toLocalDate();
                if (!eventDate.isBefore(today)) {
                    result.add(eventDate);
                }
            }
            return result;
        }

        if (event.dayOfWeek() == null) return result;

        for (int d = 0; d <= maxDays; d++) {
            LocalDate date = today.plusDays(d);
            int dow = date.getDayOfWeek().getValue();
            if (dow != event.dayOfWeek()) continue;

            boolean matches =
                    switch (event.eventType()) {
                        case RECURRING -> true;
                        case MONTHLY_FIRST -> date.getDayOfMonth() <= 7;
                        case QUARTERLY -> date.getDayOfMonth() <= 7 && (date.getMonthValue() - 1) % 3 == 0;
                        case YEARLY ->
                            event.startTime() != null
                                    && event.startTime().atZone(ZoneOffset.UTC).getMonthValue() == date.getMonthValue()
                                    && event.startTime().atZone(ZoneOffset.UTC).getDayOfMonth() == date.getDayOfMonth();
                        default -> false;
                    };
            if (matches) result.add(date);
        }
        return result;
    }

    private List<Integer> resolveTargetMembers(StationEvent event, LocalDate eventDate) {
        if (event.requiresRegistration()) {
            return eventRepository.findRegisteredMemberIds(event.id());
        }
        var allMembers = stationMemberRepository.findByStation(event.stationId());
        var declinedIds = new HashSet<>(eventRepository.findDeclinedMemberIds(event.id(), eventDate));
        return allMembers.stream()
                .map(StationMember::id)
                .filter(id -> !declinedIds.contains(id))
                .toList();
    }
}
