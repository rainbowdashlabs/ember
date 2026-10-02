/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventChanged;
import dev.chojo.ember.feature.events.service.EventMoveService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Brings what hangs off an appointment's dates along when the appointment moves.
 *
 * <p>Only a move matters here: a new time, weekday, repetition or end of the series. A rename or a new
 * description leaves every date where it was, so nothing filed against one has to change.
 *
 * <p>The service that follows a move reads member names, and those are built on services that publish
 * events themselves. Asking for it while the handler is being built would ask for something still
 * being built, so it is asked for when a change actually arrives.
 */
@Singleton
public class EventChangedHandler implements DomainEventHandler<EventChanged> {
    private final Provider<EventMoveService> moveService;

    @Inject
    public EventChangedHandler(Provider<EventMoveService> moveService) {
        this.moveService = moveService;
    }

    @Override
    public Class<EventChanged> eventType() {
        return EventChanged.class;
    }

    @Override
    public void handle(EventChanged event) {
        if (!event.moved()) return;
        moveService.get().followMove(event.before(), event.after());
    }
}
