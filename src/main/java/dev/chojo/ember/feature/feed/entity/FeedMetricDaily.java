/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

import java.time.LocalDate;

/**
 * Daily feed render histogram and totals per {@code (type, status)}.
 */
public record FeedMetricDaily(
        LocalDate day,
        String type,
        int status,
        long count,
        long totalDurationMs,
        long totalEntries,
        long bucketLt50,
        long bucketLt200,
        long bucketLt1000,
        long bucketLt5000,
        long bucketGte5000) {}
