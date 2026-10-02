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
    /**
     * Not visible to anyone.
     */
    NONE,
    /**
     * Visible to other stations on the same instance.
     */
    INSTANCE,
    /**
     * Visible to everyone (including cross-instance).
     */
    PUBLIC;

    /**
     * What a station somebody founds on this instance starts with: listed publicly, until it decides
     * otherwise in its setup or its settings.
     *
     * <p>Only a newly founded station takes it. A station that arrives by import or transfer keeps the
     * setting it carries, and a station that existed before keeps its own, since listing either would
     * publish a station nobody agreed to publish.
     */
    public static final DiscoveryVisibility NEW_STATION_DEFAULT = PUBLIC;
}
