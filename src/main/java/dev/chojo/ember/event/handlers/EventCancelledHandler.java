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
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;

/**
 * Tells the members holding a place that an appointment was called off. Places on dates already
 * behind the station are not news to anybody, so only the ones from today on count.
 */
@Singleton
public class EventCancelledHandler implements DomainEventHandler<EventCancelled> {
    private final NotificationService notificationService;
    private final EventRegistrationRepository registrationRepository;
    private final OccurrenceCalendar occurrenceCalendar;

    @Inject
    public EventCancelledHandler(
            NotificationService notificationService,
            EventRegistrationRepository registrationRepository,
            OccurrenceCalendar occurrenceCalendar) {
        this.notificationService = notificationService;
        this.registrationRepository = registrationRepository;
        this.occurrenceCalendar = occurrenceCalendar;
    }

    @Override
    public Class<EventCancelled> eventType() {
        return EventCancelled.class;
    }

    @Override
    public void handle(EventCancelled event) {
        var today = LocalDate.now(occurrenceCalendar.zoneOf(event.stationId()));
        var memberIds = registrationRepository.findRegisteredMemberIdsFrom(event.eventId(), today);
        for (int memberId : memberIds) {
            var link = NotificationLinks.event(event.eventId());
            notificationService.notify(
                    memberId,
                    NotificationType.EVENT_CANCELLED,
                    NotificationData.of(
                            new NotificationParams.EventCancelled(event.eventName(), event.reason()), link));
        }
    }
}
