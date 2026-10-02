/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.feed.entity;

import java.time.Instant;

/**
 * Aggregated per-user-agent statistics row.
 */
public record FeedUserAgentStat(
        String uaHash, String uaString, long requestCount, Instant firstSeen, Instant lastSeen) {}
