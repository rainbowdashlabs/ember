/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import dev.chojo.ember.util.Json;

/**
 * Tile provider configuration, persisted as JSON in the {@code application_setting} table
 * under the key {@code maps_tiles}.
 *
 * <p>Unset URL template / attribution fall back to the provider's defaults at read time;
 * the persisted record stores whatever the operator typed.
 */
public record MapsTilesConfig(
        MapTileProvider provider, String apiKey, String urlTemplate, String attribution, int minZoom, int maxZoom) {

    public static final MapsTilesConfig DEFAULT = new MapsTilesConfig(MapTileProvider.OSM, "", "", "", 0, 19);

    public static MapsTilesConfig parse(String json) {
        try {
            return Json.MAPPER.readValue(json, MapsTilesConfig.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MapsTilesConfig", e);
        }
    }

    public String toJson() {
        try {
            return Json.MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MapsTilesConfig", e);
        }
    }

    /**
     * Returns the URL template the operator actually configured, falling back to the
     * provider's baked-in default if the operator left the override blank.
     */
    public String resolvedUrlTemplate() {
        if (urlTemplate != null && !urlTemplate.isBlank()) return urlTemplate;
        return provider != null ? provider.defaultUrlTemplate() : null;
    }

    /**
     * Returns the attribution the operator actually configured, falling back to the
     * provider's baked-in default. Always non-null when the provider is non-null.
     */
    public String resolvedAttribution() {
        if (attribution != null && !attribution.isBlank()) return attribution;
        return provider != null ? provider.defaultAttribution() : null;
    }
}
