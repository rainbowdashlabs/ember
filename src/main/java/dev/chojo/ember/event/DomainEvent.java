/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event;

/**
 * Marker interface for all domain events.
 * Events are published by services after successful state changes
 * and consumed by handlers (e.g., notification creation).
 */
public interface DomainEvent {
    /**
     * The station this event occurred in, or 0 for system-level events.
     */
    int stationId();
}
