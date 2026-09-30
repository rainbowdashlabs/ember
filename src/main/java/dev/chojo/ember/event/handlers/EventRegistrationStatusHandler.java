/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventRegistrationStatusChanged;
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
 * Tells a member that their registration changed, every time, and the event managers once while
 * they have not read the last word about it.
 */
@Singleton
public class EventRegistrationStatusHandler implements DomainEventHandler<EventRegistrationStatusChanged> {
    private final Notifier notifier;

    @Inject
    public EventRegistrationStatusHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<EventRegistrationStatusChanged> eventType() {
        return EventRegistrationStatusChanged.class;
    }

    @Override
    public void handle(EventRegistrationStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.EventRegistrationStatus(
                        event.memberName(), event.eventName(), event.newStatus(), null),
                NotificationLinks.event(event.eventId()));

        notifier.notify(
                StationAudience.member(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.EVERY_TIME);
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.EVENT_MANAGER)
                        .except(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
