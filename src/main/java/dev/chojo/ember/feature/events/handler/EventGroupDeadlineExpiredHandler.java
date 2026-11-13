/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventGroupDeadlineExpired;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Tells whoever runs events that sign-ups for an event group closed while some still wait for a decision. */
@Singleton
public class EventGroupDeadlineExpiredHandler implements DomainEventHandler<EventGroupDeadlineExpired> {
    private final Notifier notifier;

    @Inject
    public EventGroupDeadlineExpiredHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<EventGroupDeadlineExpired> eventType() {
        return EventGroupDeadlineExpired.class;
    }

    @Override
    public void handle(EventGroupDeadlineExpired event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.EVENT_MANAGER),
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                NotificationData.of(
                        new NotificationParams.RegistrationDeadlineExpired(
                                event.eventGroupName(), event.pendingCount()),
                        NotificationLinks.eventGroup(event.eventGroupId())),
                Delivery.EVERY_TIME);
    }
}
