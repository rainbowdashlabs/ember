/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import dev.chojo.ember.util.Json;

/**
 * Geocoding configuration persisted as JSON under {@code application_setting.maps_geocoding}.
 * Disabled by default; the slot exists so v2 can wire in a forward-geocoding UI without a
 * schema change.
 */
public record MapsGeocodingConfig(GeocodingProvider provider, String apiKey, String contactEmail) {

    public static final MapsGeocodingConfig DEFAULT = new MapsGeocodingConfig(GeocodingProvider.NONE, "", "");

    public static MapsGeocodingConfig parse(String json) {
        try {
            return Json.MAPPER.readValue(json, MapsGeocodingConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MapsGeocodingConfig", e);
        }
    }

    public String toJson() {
        try {
            return Json.MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MapsGeocodingConfig", e);
        }
    }
}
