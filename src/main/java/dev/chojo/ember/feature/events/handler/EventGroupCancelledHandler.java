/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventGroupCancelled;
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
 * Tells everybody whose sign-up held or waited for a place that a whole event group was called off, once
 * for the event group. The parts called off with it tell nobody on their own.
 */
@Singleton
public class EventGroupCancelledHandler implements DomainEventHandler<EventGroupCancelled> {
    private final Notifier notifier;

    @Inject
    public EventGroupCancelledHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<EventGroupCancelled> eventType() {
        return EventGroupCancelled.class;
    }

    @Override
    public void handle(EventGroupCancelled event) {
        if (event.memberIds().isEmpty()) return;
        notifier.notify(
                StationAudience.household(event.memberIds()),
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled(event.eventGroupName(), event.reason(), null, null, true),
                        NotificationLinks.eventGroup(event.eventGroupId())),
                Delivery.EVERY_TIME);
    }
}
