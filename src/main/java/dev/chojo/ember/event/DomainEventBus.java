/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simple synchronous event bus for domain events.
 * <p>
 * Handlers are registered via Guice multibinding and auto-subscribed on construction.
 * Events are dispatched synchronously - handlers run in the caller's thread.
 * Handler exceptions are logged and do not propagate to the publisher.
 */
@Singleton
public class DomainEventBus {
    private static final Logger log = LoggerFactory.getLogger(DomainEventBus.class);

    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<?>>> handlers = new HashMap<>();

    @Inject
    public DomainEventBus(Set<DomainEventHandler<?>> registeredHandlers) {
        for (var handler : registeredHandlers) {
            handlers.computeIfAbsent(handler.eventType(), _ -> new ArrayList<>())
                    .add(handler);
        }
        log.info(
                "Domain event bus initialized with {} handler(s) for {} event type(s)",
                registeredHandlers.size(),
                handlers.size());
    }

    /**
     * Publish an event to all registered handlers.
     * Handlers are invoked synchronously. Exceptions in one handler do not prevent others from running.
     */
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> void publish(T event) {
        var eventHandlers = handlers.get(event.getClass());
        if (eventHandlers == null || eventHandlers.isEmpty()) return;

        for (var handler : eventHandlers) {
            try {
                ((DomainEventHandler<T>) handler).handle(event);
            } catch (Exception e) {
                log.error(
                        "Error in event handler {} for event {}",
                        handler.getClass().getSimpleName(),
                        event.getClass().getSimpleName(),
                        e);
            }
        }
    }
}
