/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;

public record EventRegistrationStatusChanged(
        int stationId, int eventId, String eventName, int memberId, RegistrationStatus newStatus)
        implements DomainEvent {}
