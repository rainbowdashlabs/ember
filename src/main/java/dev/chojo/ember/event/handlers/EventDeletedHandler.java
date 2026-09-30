/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.handlers;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventDeleted;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class EventDeletedHandler implements DomainEventHandler<EventDeleted> {
    private final Notifier notifier;

    @Inject
    public EventDeletedHandler(Notifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Class<EventDeleted> eventType() {
        return EventDeleted.class;
    }

    /**
     * Takes the appointment's notifications with it: its announcement, its answers and everything
     * written under it point at the page that has just gone, and its reminders point at one of its
     * dates on that same page.
     */
    @Override
    public void handle(EventDeleted event) {
        notifier.withdrawAll(NotificationLinks.event(event.eventId()));
        notifier.withdrawAll(NotificationLinks.eventDates(event.eventId()));
    }
}
