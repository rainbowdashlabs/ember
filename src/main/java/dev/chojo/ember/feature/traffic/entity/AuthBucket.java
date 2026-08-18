/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.traffic.entity;

/**
 * Classification of a request for traffic accounting. Stored as the {@code auth} column
 * value in {@code station_traffic_hourly}. The split lets an operator answer questions like
 * "how much of my egress is real users vs. federation peers" without keeping per-request
 * data.
 */
public enum AuthBucket {
    /**
     * Authenticated app traffic - a logged-in user session on a non-public route.
     */
    AUTHENTICATED,
    /**
     * Public traffic - anonymous visitors, feeds, sitemap, RSS/Atom/ICS.
     */
    UNAUTHENTICATED,
    /**
     * Server-to-server federation traffic - signature-authenticated peer instances.
     */
    FEDERATION
}
