/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * The log lines one instance sent with a fault, as a beacon keeps them.
 *
 * @param instanceId  the instance that sent them
 * @param contactName the name that instance gave to be answered on, or null
 * @param version     the version it last reported, or null
 * @param occurredAt  when the occurrence the lines surround happened, on that instance
 * @param receivedAt  when the beacon received them
 * @param lines       the lines, oldest first, as the instance redacted them
 */
public record BeaconProblemContext(
        String instanceId,
        @Nullable String contactName,
        @Nullable String version,
        Instant occurredAt,
        Instant receivedAt,
        List<BeaconPayloads.LogLinePayload> lines) {}
