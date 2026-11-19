/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupPartState;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Calls off the event groups that too few sign-ups were accepted for in time.
 *
 * <p>An event group with a minimum says how many days before its first part the minimum must be
 * reached. The first part is the earliest one as it stands, a called-off one included, the same start
 * the sign-up deadline counts from, and the check is due from midnight that many days before it on the
 * station's calendar, as for an appointment.
 * Every half hour each such event group inside that stretch is counted: the accepted sign-ups of this
 * station and of partner stations alike. One that falls short is called off as a whole, with every
 * part still to come and the notice a cancellation sends. Once its first part has begun the event
 * group is left alone, and a called off event group is never checked again.
 */
@Singleton
public class EventGroupThresholdChecker implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(EventGroupThresholdChecker.class);

    private final EventGroupRepository eventGroups;
    private final EventGroupRegistrationService registrations;
    private final EventGroupCancellationService cancellation;
    private final OccurrenceCalendar calendar;
    private final StationReadOnlyGuard readOnlyGuard;

    @Inject
    public EventGroupThresholdChecker(
            EventGroupRepository eventGroups,
            EventGroupRegistrationService registrations,
            EventGroupCancellationService cancellation,
            OccurrenceCalendar calendar,
            StationReadOnlyGuard readOnlyGuard) {
        this.eventGroups = eventGroups;
        this.registrations = registrations;
        this.cancellation = cancellation;
        this.calendar = calendar;
        this.readOnlyGuard = readOnlyGuard;
    }

    /** One sweep over every event group with a minimum. */
    void check() {
        for (var eventGroup : eventGroups.findWithMinimum()) {
            try {
                if (readOnlyGuard.isWritable(eventGroup.stationId())) checkEventGroup(eventGroup);
            } catch (RuntimeException e) {
                log.error("Error checking the minimum of event group {}", eventGroup.id(), e);
            }
        }
    }

    private void checkEventGroup(EventGroup eventGroup) {
        Integer minimum = eventGroup.details().minRegistrations();
        Integer days = eventGroup.details().thresholdDays();
        if (minimum == null || days == null) return;
        var firstStart = firstStart(eventGroup);
        if (firstStart.isEmpty()) return;
        var zone = calendar.zoneOf(eventGroup.stationId());
        Instant now = Instant.now();
        var from = RegistrationDeadline.daysBeforeAtMidnight(days).closesAt(firstStart.get(), zone);
        if (from.isEmpty() || !now.isBefore(firstStart.get()) || now.isBefore(from.get())) return;
        int accepted = accepted(eventGroup.id());
        if (accepted >= minimum) return;
        if (cancellation.cancelForTooFewSignUps(
                eventGroup.stationId(), eventGroup.id(), () -> stillShort(eventGroup.id()))) {
            log.info(
                    "Cancelled event group {} (id={}): {} of {} sign-ups accepted",
                    eventGroup.details().name(),
                    eventGroup.id(),
                    accepted,
                    minimum);
        }
    }

    /**
     * The start of the event group: its earliest part as it stands, a called-off one included, the
     * same start its sign-up deadline counts from.
     */
    private Optional<Instant> firstStart(EventGroup eventGroup) {
        return registrations.partStates(eventGroup.id()).stream()
                .map(EventGroupPartState::startTime)
                .min(Comparator.naturalOrder());
    }

    private int accepted(int eventGroupId) {
        return eventGroups.countSignUps(eventGroupId, Set.of(RegistrationStatus.ACCEPTED));
    }

    /**
     * Whether the event group, read again under its lock, still carries a minimum, is not called off and
     * falls short of it.
     */
    private boolean stillShort(int eventGroupId) {
        var current = eventGroups.findById(eventGroupId).orElse(null);
        if (current == null || current.cancelled()) return false;
        Integer minimum = current.details().minRegistrations();
        return minimum != null && current.details().thresholdDays() != null && accepted(eventGroupId) < minimum;
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(new ScheduledTask(
                "event-group-threshold-check",
                Schedule.fixedDelay(Duration.ofMinutes(7), Duration.ofMinutes(30)),
                this::check));
    }
}
