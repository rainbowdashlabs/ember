/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;

import java.util.List;

/**
 * Emitted by {@code BatchEventService} after a bulk-creation finished. Replaces the per-event
 * {@link EventCreated} fan-out for batch flows so members receive a single aggregate notification
 * instead of one per row.
 */
public record EventsBatchCreated(int stationId, List<StationEvent> events) implements DomainEvent {}
