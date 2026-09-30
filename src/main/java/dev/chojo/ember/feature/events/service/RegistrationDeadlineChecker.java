/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;

@Singleton
public class RegistrationDeadlineChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDeadlineChecker.class);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventRegistrationService registrationService;
    private final DomainEventBus eventBus;
    private final OccurrenceCalendar occurrenceCalendar;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public RegistrationDeadlineChecker(
            EventRepository eventRepository,
            EventRegistrationRepository registrationRepository,
            EventRegistrationService registrationService,
            DomainEventBus eventBus,
            OccurrenceCalendar occurrenceCalendar,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.registrationService = registrationService;
        this.eventBus = eventBus;
        this.occurrenceCalendar = occurrenceCalendar;
        this.readOnlyGuard = readOnlyGuard;
    }

    private void check() {
        try {
            checkOneTimeEvents();
            checkRecurringEvents();
        } catch (Exception e) {
            log.error("Error checking registration deadlines", e);
        }
    }

    private void checkOneTimeEvents() {
        var expired = eventRepository.findOneTimeEventsWithExpiredDeadline();
        for (var entry : expired) {
            if (!readOnlyGuard.isWritable(entry.stationId())) continue;
            eventRepository.markDeadlineNotified(entry.eventId());
            eventBus.publish(new RegistrationDeadlineExpired(
                    entry.stationId(), entry.eventId(), entry.name(), entry.pendingCount()));
            log.info(
                    "Registration deadline expired for one-time event '{}' (id={}) with {} pending registrations",
                    entry.name(),
                    entry.eventId(),
                    entry.pendingCount());
        }
    }

    /**
     * Which repeating appointments are about to close their list.
     *
     * <p>Each station is asked about on its own clock. Read on the server's, a station two hours ahead
     * spends the last two hours of its evening being told about yesterday, and the appointment whose
     * list closes at midnight closes it on the wrong day.
     *
     * <p>A next date that was called off closes nothing: its places are kept as they stand, so they
     * are still there if the date is brought back.
     */
    private void checkRecurringEvents() {
        var events = eventRepository.findRecurringEventsWithCloseDays();
        var calendars = new HashMap<Integer, StationCalendar>();

        for (var event : events) {
            if (!readOnlyGuard.isWritable(event.stationId())) continue;
            var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
            var today = calendar.today();
            var next = calendar.next(event, today);
            if (next.isEmpty()) continue;
            var nextDate = next.get();
            if (calendar.isCancelled(event, nextDate)) continue;

            var deadlineDate = nextDate.minusDays(event.registrationCloseDays());
            if (today.isBefore(deadlineDate)) continue;

            var pending = registrationRepository.findPendingByEventAndDate(event.id(), nextDate);
            if (pending.isEmpty()) continue;

            for (EventRegistration reg : pending) {
                registrationService.decline(event.id(), reg.memberId(), nextDate, null);
            }
            eventBus.publish(
                    new RegistrationDeadlineExpired(event.stationId(), event.id(), event.name(), pending.size()));
            log.info(
                    "Auto-declined {} pending registrations for recurring event '{}' (id={}) on {}",
                    pending.size(),
                    event.name(),
                    event.id(),
                    nextDate);
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "registration-deadline-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)),
                this::check));
    }
}
