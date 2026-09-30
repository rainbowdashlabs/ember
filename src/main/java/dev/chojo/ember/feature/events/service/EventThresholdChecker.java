/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.StationCalendar;
import dev.chojo.ember.feature.events.entity.StationEvent;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

/**
 * Calls off the dates that too few registrations were accepted for in time, one date at a time.
 *
 * <p>An appointment with a minimum says how many days before each date the minimum must be reached.
 * Every half hour each date inside that stretch, read on the station's calendar, is counted on its
 * own: the accepted registrations of that date and no other. A date that falls short is called off,
 * and every other date of the series stays as it is.
 *
 * <p>A date that was ever called off is never checked again. That is what keeps a date a manager
 * brought back from being called off by the next sweep.
 */
@Singleton
public class EventThresholdChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EventThresholdChecker.class);

    private final EventRepository eventRepository;
    private final EventRegistrationRepository registrationRepository;
    private final EventCancellationService cancellationService;
    private final OccurrenceCalendar occurrenceCalendar;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public EventThresholdChecker(
            EventRepository eventRepository,
            EventRegistrationRepository registrationRepository,
            EventCancellationService cancellationService,
            OccurrenceCalendar occurrenceCalendar,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.cancellationService = cancellationService;
        this.occurrenceCalendar = occurrenceCalendar;
        this.readOnlyGuard = readOnlyGuard;
    }

    /** One sweep over every appointment with a minimum, each station read on its own calendar. */
    void check() {
        try {
            var calendars = new HashMap<Integer, StationCalendar>();
            for (var event : eventRepository.findThresholdCandidates()) {
                if (!readOnlyGuard.isWritable(event.stationId())) continue;
                var calendar = calendars.computeIfAbsent(event.stationId(), occurrenceCalendar::forStation);
                checkDates(event, calendar);
            }
        } catch (Exception e) {
            log.error("Error checking event thresholds", e);
        }
    }

    private void checkDates(StationEvent event, StationCalendar calendar) {
        LocalDate today = calendar.today();
        for (var date : calendar.between(event, today, today.plusDays(event.thresholdDays()))) {
            if (!calendar.takesPlaceOn(event, date)) continue;
            int accepted = registrationRepository.countAccepted(event.id(), date);
            if (accepted >= event.minRegistrations()) continue;
            if (cancellationService.cancelForTooFewRegistrations(event, date)) {
                log.info(
                        "Cancelled {} of event {} (id={}): {} of {} registrations accepted",
                        date,
                        event.name(),
                        event.id(),
                        accepted,
                        event.minRegistrations());
            }
        }
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "event-threshold-check",
                Schedule.fixedDelay(Duration.ofMinutes(5), Duration.ofMinutes(30)),
                this::check));
    }
}
