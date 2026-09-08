/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;

/**
 * A registration changed state.
 *
 * @param memberName the member's name, carried so the notification means something to an event
 *                   manager, who is told about somebody else's registration and cannot resolve an
 *                   identifier
 */
public record EventRegistrationStatusChanged(
        int stationId,
        int eventId,
        String eventName,
        int memberId,
        String memberName,
        RegistrationStatus newStatus)
        implements DomainEvent {}
