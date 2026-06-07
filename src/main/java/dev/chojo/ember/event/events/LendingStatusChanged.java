/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.federation.entity.LendingStatus;
import dev.chojo.ember.feature.notifications.entity.NotificationType;

public record LendingStatusChanged(
        int stationId,
        int targetStationId,
        int requestId,
        NotificationType type,
        String stationName,
        LendingStatus status)
        implements DomainEvent {}
