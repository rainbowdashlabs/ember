/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * This station decided about a partner station's member on one date of an appointment, or the member
 * moved up from the waiting list. Published once the change is kept, so the partner can be notified.
 *
 * @param stationId      the station holding it, which decided
 * @param registrationId the partner's registration
 * @param status         the status it holds now
 * @param queuePosition  the place in line where it now waits on the waiting list, or null
 * @param movedUp        whether it moved up from the waiting list into a place that fell free
 * @param changedAt      when this station decided, taken as the change was made
 */
public record PartnerRegistrationStatusChanged(
        int stationId,
        int registrationId,
        RegistrationStatus status,
        @Nullable Integer queuePosition,
        boolean movedUp,
        Instant changedAt)
        implements DomainEvent {}
