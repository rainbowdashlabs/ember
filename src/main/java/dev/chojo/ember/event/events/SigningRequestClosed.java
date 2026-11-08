/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * A signature request no longer waits for anybody: every field was signed, confirmed on paper, waived or
 * withdrawn.
 *
 * @param stationId    the station
 * @param requestId    the request
 * @param generationId the generated document it asked to sign, or null for one not generated here
 * @param agreed       whether it stands as agreed: no field is open and at least one was signed, confirmed
 *                     on paper or waived
 * @param settledBy    the member who settled the last field or withdrew the rest
 */
public record SigningRequestClosed(
        int stationId, int requestId, @Nullable Integer generationId, boolean agreed, int settledBy)
        implements DomainEvent {}
