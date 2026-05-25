/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event;

/**
 * Handles a specific type of domain event.
 * Implementations are registered with the {@link DomainEventBus} and invoked
 * synchronously when matching events are published.
 *
 * @param <T> the event type this handler processes
 */
public interface DomainEventHandler<T extends DomainEvent> {
    /**
     * The event class this handler is interested in.
     */
    Class<T> eventType();

    /**
     * Handle the event. Called synchronously by the event bus.
     * Exceptions are logged but do not prevent other handlers from running.
     */
    void handle(T event);
}
