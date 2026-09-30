/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventCancelled;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

/**
 * Tells the members holding a place that an appointment was called off, and whoever looks after them.
 *
 * <p>For one date that is whoever holds a place on that date and nobody else: a place on another date
 * of the series still stands. For a whole series it is whoever holds a place on any date from today
 * on, since places on dates already behind the station are not news to anybody.
 */
@Singleton
public class EventCancelledHandler implements DomainEventHandler<EventCancelled> {
    private final NotificationService notificationService;
    private final EventRegistrationRepository registrationRepository;
    private final OccurrenceCalendar occurrenceCalendar;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public EventCancelledHandler(
            NotificationService notificationService,
            EventRegistrationRepository registrationRepository,
            OccurrenceCalendar occurrenceCalendar,
            GuardianPolicy guardianPolicy) {
        this.notificationService = notificationService;
        this.registrationRepository = registrationRepository;
        this.occurrenceCalendar = occurrenceCalendar;
        this.guardianPolicy = guardianPolicy;
    }

    @Override
    public Class<EventCancelled> eventType() {
        return EventCancelled.class;
    }

    @Override
    public void handle(EventCancelled event) {
        var audience = guardianPolicy.withGuardians(placeHolders(event));
        if (audience.isEmpty()) return;
        var link = event.eventDate() != null
                ? NotificationLinks.eventDate(event.eventId(), event.eventDate())
                : NotificationLinks.event(event.eventId());
        notificationService.notifyMembers(
                audience,
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled(
                                event.eventName(), event.reason(), event.eventDate(), event.cause()),
                        link));
    }

    private List<Integer> placeHolders(EventCancelled event) {
        if (event.eventDate() != null) {
            return registrationRepository.findRegisteredMemberIds(event.eventId(), event.eventDate());
        }
        var today = LocalDate.now(occurrenceCalendar.zoneOf(event.stationId()));
        return registrationRepository.findRegisteredMemberIdsFrom(event.eventId(), today);
    }
}
