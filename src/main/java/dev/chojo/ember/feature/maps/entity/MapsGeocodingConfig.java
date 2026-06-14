/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import tools.jackson.databind.json.JsonMapper;

/**
 * Geocoding configuration persisted as JSON under {@code application_setting.maps_geocoding}.
 * See {@code .concept/geolocation.md} §4.4. Disabled by default; the slot exists so v2 can
 * wire in a forward-geocoding UI without a schema change.
 */
public record MapsGeocodingConfig(GeocodingProvider provider, String apiKey, String contactEmail) {

    public static final MapsGeocodingConfig DEFAULT = new MapsGeocodingConfig(GeocodingProvider.NONE, "", "");

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MapsGeocodingConfig", e);
        }
    }

    public static MapsGeocodingConfig parse(String json) {
        try {
            return MAPPER.readValue(json, MapsGeocodingConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MapsGeocodingConfig", e);
        }
    }
}
