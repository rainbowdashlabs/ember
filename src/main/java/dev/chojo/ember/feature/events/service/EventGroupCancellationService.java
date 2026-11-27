/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.EventGroupCancelled;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BooleanSupplier;

/**
 * Calls a whole event group off.
 *
 * <p>Every part still to come is called off with it, the way a single date is, so lists, the
 * calendar, attendance and reminders know. The people signed up are notified once, for the event group; the
 * parts notify nobody on their own. Parts that already took place or were called off before are left
 * as they are. Calling an event group off is final: it cannot be taken back, and none of its parts comes
 * back on its own.
 */
@Singleton
public class EventGroupCancellationService {
    private static final Logger log = LoggerFactory.getLogger(EventGroupCancellationService.class);

    private final EventGroupRepository eventGroups;
    private final EventGroupService eventGroupService;
    private final EventGroupRegistrationRepository signUps;
    private final EventRepository events;
    private final EventCancellationService cancellation;
    private final OccurrenceCalendar calendar;
    private final DomainEventBus eventBus;

    @Inject
    public EventGroupCancellationService(
            EventGroupRepository eventGroups,
            EventGroupRegistrationRepository signUps,
            EventRepository events,
            EventCancellationService cancellation,
            OccurrenceCalendar calendar,
            DomainEventBus eventBus,
            EventGroupService eventGroupService) {
        this.eventGroups = eventGroups;
        this.eventGroupService = eventGroupService;
        this.signUps = signUps;
        this.events = events;
        this.cancellation = cancellation;
        this.calendar = calendar;
        this.eventBus = eventBus;
    }

    /**
     * Calls an event group off with every part still to come.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param reason       why, as shown to the people signed up, or null
     * @param cancelledBy  the member calling it off, or null
     */
    public void cancel(int stationId, int eventGroupId, @Nullable String reason, @Nullable Integer cancelledBy) {
        if (!callOff(stationId, eventGroupId, reason, cancelledBy, CancellationCause.MANUAL, () -> true)) {
            throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
        }
    }

    /**
     * Calls an event group off because too few sign-ups were accepted in time. The people signed up
     * are notified, worded in their own language, and no reason is stored.
     *
     * <p>Whether it is still short is asked again under the event group's lock, so a minimum taken off or a
     * sign-up accepted since the check read it keeps the event group.
     *
     * @param stationId    the station holding it
     * @param eventGroupId the event group
     * @param stillShort   whether it still falls short, read under the lock
     * @return true where it was called off now, false where it was off already or no longer short
     */
    public boolean cancelForTooFewSignUps(int stationId, int eventGroupId, BooleanSupplier stillShort) {
        return callOff(stationId, eventGroupId, null, null, CancellationCause.THRESHOLD, stillShort);
    }

    private boolean callOff(
            int stationId,
            int eventGroupId,
            @Nullable String reason,
            @Nullable Integer cancelledBy,
            CancellationCause cause,
            BooleanSupplier stillWanted) {
        var eventGroup = Transactions.call(() -> {
            var locked = eventGroupService.lockEventGroup(stationId, eventGroupId);
            if (!stillWanted.getAsBoolean()) return null;
            return eventGroups.cancel(eventGroupId, reason) ? locked : null;
        });
        if (eventGroup == null) return false;
        try {
            for (var part : eventGroups.findParts(eventGroupId)) {
                callOffPart(stationId, part.eventId(), reason, cancelledBy);
            }
        } finally {
            eventBus.publish(new EventGroupCancelled(
                    stationId,
                    eventGroupId,
                    eventGroup.details().name(),
                    reason,
                    signUps.findHoldingMemberIds(eventGroupId),
                    cause));
        }
        return true;
    }

    /**
     * Calls one part off unless it is off already or past. A part that cannot be called off is left
     * as it is and the others still follow, so one part never keeps the event group or its notice back.
     */
    private void callOffPart(int stationId, int eventId, @Nullable String reason, @Nullable Integer cancelledBy) {
        var stationCalendar = calendar.forStation(stationId);
        try {
            events.findById(eventId)
                    .filter(event -> !stationCalendar.cancelledAltogether(event))
                    .ifPresent(event -> stationCalendar
                            .ruleOf(event)
                            .filter(rule -> !rule.first().isBefore(stationCalendar.today()))
                            .ifPresent(rule -> cancellation.cancelDate(event, rule.first(), reason, cancelledBy)));
        } catch (RuntimeException e) {
            log.warn("Could not call off part {} with its event group", eventId, e);
        }
    }
}
