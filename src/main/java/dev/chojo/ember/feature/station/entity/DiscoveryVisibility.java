/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

/**
 * Controls whether a station is visible in the federation discovery.
 */
public enum DiscoveryVisibility {
    /** Not visible to anyone. */
    NONE,
    /** Visible to other stations on the same instance. */
    INSTANCE,
    /** Visible to everyone (including cross-instance). */
    PUBLIC
}
