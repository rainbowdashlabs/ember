/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

public record ExchangeRequested(
        int stationId, int exchangeId, int memberId, String memberName, String inventoryName, String reason)
        implements DomainEvent {}
