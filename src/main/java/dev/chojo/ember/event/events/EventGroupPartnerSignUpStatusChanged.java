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
 * This station decided about a partner station's member's event group sign-up, or the sign-up moved up
 * from the waiting list. Published once the change is kept, so the partner can be told.
 *
 * @param stationId      the station holding it, which decided
 * @param signUpId      the partner's event group sign-up
 * @param status        the status it holds now
 * @param queuePosition the place in line where it now waits on the waiting list, or null
 * @param movedUp       whether it moved up from the waiting list into a place that fell free
 * @param changedAt      when this station decided, taken as the change was made
 */
public record EventGroupPartnerSignUpStatusChanged(
        int stationId,
        int signUpId,
        RegistrationStatus status,
        @Nullable Integer queuePosition,
        boolean movedUp,
        Instant changedAt)
        implements DomainEvent {}
