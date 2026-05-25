/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

public record LendingRequested(
        int stationId, int owningStationId, int requestId, String requestingStationName, String itemSummary)
        implements DomainEvent {}
