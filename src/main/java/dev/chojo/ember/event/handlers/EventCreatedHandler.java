/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventCreated;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;

@Singleton
public class EventCreatedHandler implements DomainEventHandler<EventCreated> {
    private final NotificationService notificationService;

    @Inject
    public EventCreatedHandler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Override
    public Class<EventCreated> eventType() {
        return EventCreated.class;
    }

    @Override
    public void handle(EventCreated event) {
        var e = event.event();
        String description = "";
        if (e.description() != null && !e.description().isBlank()) {
            description = e.description().length() > 80 ? e.description().substring(0, 80) + "..." : e.description();
        }
        notificationService.notifyStation(
                event.stationId(),
                NotificationType.NEW_EVENT,
                NotificationData.of(
                        new NotificationParams.NewEvent(e.name(), description),
                        new NotificationData.NotificationLink("event-detail", Map.of("id", e.id()))));
    }
}
