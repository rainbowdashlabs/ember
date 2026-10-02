/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.entity;

import org.jspecify.annotations.Nullable;

/**
 * A day of one subject's bucketed counts, as a beacon collected it.
 */
public record BeaconMetricsRow(
        String metricsUid,
        String subject,
        String day,
        @Nullable String members,
        @Nullable String accounts,
        @Nullable String stations,
        @Nullable String inventory) {}
