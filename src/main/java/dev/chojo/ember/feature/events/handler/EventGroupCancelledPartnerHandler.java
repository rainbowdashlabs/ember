/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventGroupCancelled;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Tells the partner stations whose members held or waited for a place that a shared event group was
 * called off, once for the event group.
 */
@Singleton
public class EventGroupCancelledPartnerHandler implements DomainEventHandler<EventGroupCancelled> {
    private final Provider<EventGroupPartnerService> partners;

    /**
     * @param partners read when an event group is called off, since the service reaches the event bus
     *                 this handler is registered on
     */
    @Inject
    public EventGroupCancelledPartnerHandler(Provider<EventGroupPartnerService> partners) {
        this.partners = partners;
    }

    @Override
    public Class<EventGroupCancelled> eventType() {
        return EventGroupCancelled.class;
    }

    @Override
    public void handle(EventGroupCancelled event) {
        partners.get().tellCancelled(event.eventGroupId(), event.eventGroupName(), event.reason());
    }
}
