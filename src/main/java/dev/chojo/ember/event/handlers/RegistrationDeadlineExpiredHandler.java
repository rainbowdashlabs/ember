/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.RegistrationDeadlineExpired;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

/** Tells the event managers that registration closed with answers still waiting for them. */
@Singleton
public class RegistrationDeadlineExpiredHandler implements DomainEventHandler<RegistrationDeadlineExpired> {
    private final Notifier notifier;

    @Inject
    public RegistrationDeadlineExpiredHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<RegistrationDeadlineExpired> eventType() {
        return RegistrationDeadlineExpired.class;
    }

    @Override
    public void handle(RegistrationDeadlineExpired event) {
        var data = NotificationData.of(
                new NotificationParams.RegistrationDeadlineExpired(event.eventName(), event.pendingCount()),
                new NotificationData.NotificationLink("events-registrations", Map.of("id", event.eventId())));
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.EVENT_MANAGER),
                NotificationType.REGISTRATION_DEADLINE_EXPIRED,
                data,
                Delivery.EVERY_TIME);
    }
}
