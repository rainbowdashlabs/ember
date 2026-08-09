/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EventDeletedHandler implements DomainEventHandler<EventDeleted> {
    private final NotificationService notificationService;

    @Inject
    public EventDeletedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<EventDeleted> eventType() {
        return EventDeleted.class;
    }

    @Override
    public void handle(EventDeleted event) {
        notificationService.deleteByTypeContaining(
                NotificationType.NEW_EVENT,
                NotificationData.of(new NotificationParams.NewEvent(event.eventName(), null)));
    }
}
