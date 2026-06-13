/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.inventory.entity.ExchangeStatus;

public record ExchangeStatusChanged(
        int stationId,
        int exchangeId,
        int memberId,
        String memberName,
        int inventoryId,
        String inventoryName,
        ExchangeStatus newStatus)
        implements DomainEvent {}
