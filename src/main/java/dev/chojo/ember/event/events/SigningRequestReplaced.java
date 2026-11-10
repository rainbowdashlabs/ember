/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * A signature request is replaced by one for a corrected document, generated anew for the same member.
 *
 * @param stationId            the station
 * @param replacedGenerationId the document the replaced request asked to sign
 * @param generationId         the corrected document
 */
public record SigningRequestReplaced(
        int stationId,
        @Nullable Integer replacedGenerationId,
        @Nullable Integer generationId) implements DomainEvent {}
