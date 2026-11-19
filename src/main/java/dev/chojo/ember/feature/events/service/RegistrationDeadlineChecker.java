/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells the managers once per date when its sign-up deadline passed with sign-ups still waiting for
 * a decision. Nothing is declined: the waiting sign-ups stay for the managers to decide, late or not.
 *
 * <p>Each date is asked on its own and on its station's clock, since a relative deadline closes each
 * date of a series separately. A date called off closes nothing, so it is left alone and asked again
 * should it be brought back.
 */
@Singleton
public class RegistrationDeadlineChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(RegistrationDeadlineChecker.class);

    private final EventRepository eventRepository;
    private final DomainEventBus eventBus;
    private final OccurrenceCalendar occurrenceCalendar;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public RegistrationDeadlineChecker(
            EventRepository eventRepository,
            DomainEventBus eventBus,
            OccurrenceCalendar occurrenceCalendar,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.eventBus = eventBus;
        this.occurrenceCalendar = occurrenceCalendar;
        this.readOnlyGuard = readOnlyGuard;
    }

    /** Runs one sweep over every date still waiting on a decision. */
    void check() {
        try {
            var now = Instant.now();
            var calendars = new HashMap<Integer, StationCalendar>();
            var byEvent = new LinkedHashMap<Integer, List<EventRepository.PendingDate>>();
            for (var pending : eventRepository.findPendingDatesWithDeadline()) {
                byEvent.computeIfAbsent(pending.eventId(), id -> new ArrayList<>())
                        .add(pending);
            }
            byEvent.forEach((eventId, dates) ->
                    eventRepository.findById(eventId).ifPresent(event -> handle(event, dates, calendars, now)));
        } catch (RuntimeException e) {
            log.error("Error checking registration deadlines", e);
        }
    }

    /**
     * Handles the waiting dates of one appointment whose deadline passed. A fixed deadline closes
     * every date at once, so its dates are told about in one notice with their sign-ups added up;
     * a relative one closes each date on its own and is told about per date.
     */
    private void handle(
            StationEvent event,
            List<EventRepository.PendingDate> dates,
            Map<Integer, StationCalendar> calendars,
            Instant now) {
        if (!readOnlyGuard.isWritable(event.stationId())) return;
        var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
        var closed = dates.stream()
                .filter(pending -> !calendar.isCancelled(event, pending.date()))
                .filter(pending -> event.registrationClosesOn(pending.date(), calendar.zone())
                        .map(close -> !now.isBefore(close))
                        .orElse(false))
                .toList();
        if (closed.isEmpty()) return;
        closed.forEach(pending -> eventRepository.markDeadlineExpired(event.id(), pending.date()));
        if (event.registrationDeadline().kind() == RegistrationDeadline.Kind.FIXED) {
            tell(
                    event,
                    closed.stream()
                            .mapToInt(EventRepository.PendingDate::pendingCount)
                            .sum());
        } else {
            closed.forEach(pending -> tell(event, pending.pendingCount()));
        }
    }

    private void tell(StationEvent event, int pendingCount) {
        eventBus.publish(new RegistrationDeadlineExpired(event.stationId(), event.id(), event.name(), pendingCount));
        log.info(
                "Registration deadline passed for event '{}' (id={}) with {} pending registrations",
                event.name(),
                event.id(),
                pendingCount);
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "registration-deadline-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(5)),
                this::check));
    }
}
