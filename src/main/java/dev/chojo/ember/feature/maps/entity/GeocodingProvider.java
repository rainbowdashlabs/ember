/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

/**
 * Geocoding provider for the (currently UI-less) "type address → suggest pin" feature.
 * Disabled by default; the config slot is in place so a future frontend implementation
 * doesn't need a schema change.
 */
public enum GeocodingProvider {
    NONE,
    NOMINATIM,
    LOCATIONIQ,
    GEOAPIFY
}
