/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * An agreement that was signed is withdrawn again.
 *
 * @param stationId    the station
 * @param requestId    the request
 * @param generationId the generated document it was signed on, or null for one not generated here
 * @param withdrawnBy  the member who withdrew it
 */
public record SigningAgreementWithdrawn(
        int stationId, int requestId, @Nullable Integer generationId, int withdrawnBy) implements DomainEvent {}
