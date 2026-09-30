/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.WaitlistPublicRegistration;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class WaitlistPublicRegistrationHandler implements DomainEventHandler<WaitlistPublicRegistration> {
    private final Notifier notifier;

    @Inject
    public WaitlistPublicRegistrationHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<WaitlistPublicRegistration> eventType() {
        return WaitlistPublicRegistration.class;
    }

    @Override
    public void handle(WaitlistPublicRegistration event) {
        notifier.notify(
                StationAudience.holders(event.stationId(), StationPermission.WAITLIST_EDIT),
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                NotificationData.of(
                        new NotificationParams.WaitlistPublicRegistration(event.childName(), event.listName()),
                        new NotificationData.NotificationLink("waiting-lists", Map.of())),
                Delivery.EVERY_TIME);
    }
}
