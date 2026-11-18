/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.RegistrationPlacesFreed;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Moves people up from the waiting list into places that fell free without anybody giving them up.
 *
 * <p>The registration service is asked for when an event arrives, because the services that free
 * places publish events themselves and are built before it.
 */
@Singleton
public class RegistrationPlacesFreedHandler implements DomainEventHandler<RegistrationPlacesFreed> {
    private final Provider<EventRegistrationService> registrations;

    /**
     * Creates the handler.
     *
     * @param registrations the registrations, asked for when places fall free
     */
    @Inject
    public RegistrationPlacesFreedHandler(Provider<EventRegistrationService> registrations) {
        this.registrations = registrations;
    }

    @Override
    public Class<RegistrationPlacesFreed> eventType() {
        return RegistrationPlacesFreed.class;
    }

    @Override
    public void handle(RegistrationPlacesFreed event) {
        event.dates().forEach(date -> registrations.get().moveUp(event.eventId(), date));
    }
}
