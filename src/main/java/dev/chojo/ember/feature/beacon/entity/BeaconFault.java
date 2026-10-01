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
 * A fault as a beacon's overview lists it, gathered across every installation that met it.
 *
 * @param instances how many installations have reported it
 * @param versions  the versions it has been seen in, newest first
 */
public record BeaconFault(
        int id,
        String fingerprint,
        String level,
        @Nullable String exceptionClass,
        @Nullable String logger,
        @Nullable String message,
        @Nullable String frames,
        int instances,
        long occurrences,
        List<String> versions,
        Instant firstSeen,
        Instant lastSeen,
        boolean acknowledged,
        @Nullable String resolvedIn) {}
