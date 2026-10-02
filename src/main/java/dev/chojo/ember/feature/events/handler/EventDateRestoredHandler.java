/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventDateRestored;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Tells the members who kept their place on a date that was called off, and whoever looks after
 * them, that the date takes place after all. Their places were never taken away, so they are
 * expected again without having to answer anew.
 */
@Singleton
public class EventDateRestoredHandler implements DomainEventHandler<EventDateRestored> {
    private final Notifier notifier;
    private final EventRegistrationRepository registrationRepository;

    @Inject
    public EventDateRestoredHandler(Notifier notifier, EventRegistrationRepository registrationRepository) {
        this.notifier = notifier;
        this.registrationRepository = registrationRepository;
    }

    @Override
    public Class<EventDateRestored> eventType() {
        return EventDateRestored.class;
    }

    @Override
    public void handle(EventDateRestored event) {
        var placeHolders = registrationRepository.findRegisteredMemberIds(event.eventId(), event.eventDate());
        if (placeHolders.isEmpty()) return;
        notifier.notify(
                StationAudience.household(placeHolders),
                NotificationType.EVENT_DATE_RESTORED,
                NotificationData.of(
                        new NotificationParams.EventDateRestored(event.eventName(), event.eventDate()),
                        NotificationLinks.eventDate(event.eventId(), event.eventDate())),
                Delivery.EVERY_TIME);
    }
}
