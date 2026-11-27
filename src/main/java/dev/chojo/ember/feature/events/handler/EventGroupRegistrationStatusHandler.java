/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventGroupRegistrationStatusChanged;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
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
 * Notifies a member, and whoever runs events, that an event group sign-up changed: once for the event group, not
 * once for every part chosen with it. Moving up from the waiting list and being put on it are sent to
 * the member only.
 */
@Singleton
public class EventGroupRegistrationStatusHandler implements DomainEventHandler<EventGroupRegistrationStatusChanged> {
    private final Notifier notifier;

    @Inject
    public EventGroupRegistrationStatusHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<EventGroupRegistrationStatusChanged> eventType() {
        return EventGroupRegistrationStatusChanged.class;
    }

    @Override
    public void handle(EventGroupRegistrationStatusChanged event) {
        var data = NotificationData.of(
                new NotificationParams.EventRegistrationStatus(
                        event.memberName(),
                        event.eventGroupName(),
                        event.newStatus(),
                        null,
                        event.queuePosition(),
                        event.movedUp(),
                        event.shortNotice()),
                NotificationLinks.eventGroup(event.eventGroupId()));
        notifier.notify(
                StationAudience.member(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.EVERY_TIME);
        if (event.movedUp() || event.newStatus() == RegistrationStatus.QUEUED) return;
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.EVENT_MANAGER)
                        .except(event.memberId()),
                NotificationType.EVENT_REGISTRATION_STATUS,
                data,
                Delivery.ONCE_WHILE_UNREAD);
    }
}
