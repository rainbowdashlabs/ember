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
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.storage.service.StationReadOnlyGuard;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.TaskSource;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Notifies the managers once per date when its sign-up deadline passed with sign-ups still waiting for
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
     * every date at once, so its dates are reported in one notice with their sign-ups added up;
     * a relative one closes each date on its own and is reported per date.
     *
     * <p>A date already handled counts as handled only for the close it was handled for: once its
     * deadline moved and passed again, it is reported again.
     */
    private void handle(
            StationEvent event,
            List<EventRepository.PendingDate> dates,
            Map<Integer, StationCalendar> calendars,
            Instant now) {
        if (!readOnlyGuard.isWritable(event.stationId())) return;
        var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
        var closed = new ArrayList<EventRepository.PendingDate>();
        for (var pending : dates) {
            if (calendar.isCancelled(event, pending.date())) continue;
            if (!event.registrationClosedOn(pending.date(), calendar.zone(), now)) continue;
            var closes = event.registrationClosesOn(pending.date(), calendar.zone());
            if (closes.isEmpty() || closes.get().equals(pending.handledFor())) continue;
            eventRepository.markDeadlineExpired(event.id(), pending.date(), closes.get());
            closed.add(pending);
        }
        if (closed.isEmpty()) return;
        if (event.registrationDeadline().kind() == RegistrationDeadline.DeadlineKind.FIXED) {
            tell(
                    event,
                    closed.stream()
                            .mapToInt(EventRepository.PendingDate::pendingCount)
                            .sum(),
                    null);
        } else {
            closed.forEach(
                    pending -> tell(event, pending.pendingCount(), event.closesPerDate() ? pending.date() : null));
        }
    }

    /**
     * Notifies the managers, naming the date where a series closes each date on its own.
     */
    private void tell(StationEvent event, int pendingCount, @Nullable LocalDate date) {
        eventBus.publish(new RegistrationDeadlineExpired(
                event.stationId(), event.name(), pendingCount, date, NotificationLinks.eventRegistrations(event.id())));
        log.info(
                "Registration deadline passed for event '{}' (id={}) on {} with {} pending registrations",
                event.name(),
                event.id(),
                date,
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
