/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.events.entity.EventBreak;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Singleton
public class RegistrationDeadlineChecker {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDeadlineChecker.class);

    private final EventRepository eventRepository;
    private final EventService eventService;
    private final DomainEventBus eventBus;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public RegistrationDeadlineChecker(
            EventRepository eventRepository,
            EventService eventService,
            DomainEventBus eventBus,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.eventService = eventService;
        this.eventBus = eventBus;
        this.readOnlyGuard = readOnlyGuard;

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "registration-deadline-checker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::check, 5, 5, TimeUnit.MINUTES);
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

    private void checkRecurringEvents() {
        var today = LocalDate.now(ZoneOffset.UTC);
        var events = eventRepository.findRecurringEventsWithCloseDays();
        var breaks = new HashMap<Integer, List<EventBreak>>();

        for (var event : events) {
            if (!readOnlyGuard.isWritable(event.stationId())) continue;
            var stationBreaks = breaks.computeIfAbsent(event.stationId(), eventRepository::findBreaksByStation);
            var nextDate = findNextOccurrence(event, today, stationBreaks);
            if (nextDate == null) continue;

            var deadlineDate = nextDate.minusDays(event.registrationCloseDays());
            if (today.isBefore(deadlineDate)) continue;

            var pending = eventRepository.findPendingRegistrationsForDate(event.id(), nextDate);
            if (pending.isEmpty()) continue;

            for (EventRegistration reg : pending) {
                eventService.decline(event.id(), reg.memberId(), nextDate, null);
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

    private LocalDate findNextOccurrence(StationEvent event, LocalDate today, List<EventBreak> breaks) {
        if (event.dayOfWeek() == null) return null;
        for (int d = 0; d <= 28; d++) {
            var date = today.plusDays(d);
            if (EventBreak.coversAny(breaks, date)) continue;

            if (event.occursOn(date)) return date;
        }
        return null;
    }
}
