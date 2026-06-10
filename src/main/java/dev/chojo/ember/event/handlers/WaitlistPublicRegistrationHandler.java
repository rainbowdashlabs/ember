/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.WaitlistPublicRegistration;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class WaitlistPublicRegistrationHandler implements DomainEventHandler<WaitlistPublicRegistration> {
    private final NotificationService notificationService;

    @Inject
    public WaitlistPublicRegistrationHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<WaitlistPublicRegistration> eventType() {
        return WaitlistPublicRegistration.class;
    }

    @Override
    public void handle(WaitlistPublicRegistration event) {
        notificationService.notifyMembersWithRole(
                event.stationId(),
                "WAITLIST_EDIT",
                NotificationType.WAITLIST_PUBLIC_REGISTRATION,
                NotificationData.of(
                        new NotificationParams.WaitlistPublicRegistration(event.childName(), event.listName()),
                        new NotificationData.NotificationLink("waiting-lists", Map.of())));
    }
}
