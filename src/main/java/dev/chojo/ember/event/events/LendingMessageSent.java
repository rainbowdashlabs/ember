/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

public record LendingMessageSent(
        int stationId, int targetStationId, int requestId, String senderStationName, String senderName)
        implements DomainEvent {}
