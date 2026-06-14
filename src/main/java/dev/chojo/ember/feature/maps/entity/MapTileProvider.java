/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

/**
 * The tile provider the instance is currently configured against. Each variant carries the
 * canonical URL template and attribution that the backend falls back to when the operator
 * hasn't overridden them. See {@code .concept/geolocation.md} §4.4.
 *
 * <p>The {@code {z}}, {@code {x}}, {@code {y}} placeholders are interpolated by the cache
 * layer when it forwards the request upstream. The {@code {k}} placeholder is replaced with
 * the operator-supplied API key; for {@link #OSM} the key is unused and may be blank.
 */
public enum MapTileProvider {
    OSM("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", "© OpenStreetMap contributors", false),
    MAPBOX(
            "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/{z}/{x}/{y}?access_token={k}",
            "© Mapbox · © OpenStreetMap",
            true),
    STADIA(
            "https://tiles.stadiamaps.com/tiles/osm_bright/{z}/{x}/{y}.png?api_key={k}",
            "© Stadia Maps · © OpenStreetMap",
            true),
    MAPTILER(
            "https://api.maptiler.com/maps/streets-v2/256/{z}/{x}/{y}.png?key={k}",
            "© MapTiler · © OpenStreetMap",
            true),
    THUNDERFOREST(
            "https://tile.thunderforest.com/atlas/{z}/{x}/{y}.png?apikey={k}",
            "© Thunderforest · © OpenStreetMap",
            true),
    CUSTOM(null, null, false);

    private final String defaultUrlTemplate;
    private final String defaultAttribution;
    private final boolean requiresApiKey;

    MapTileProvider(String defaultUrlTemplate, String defaultAttribution, boolean requiresApiKey) {
        this.defaultUrlTemplate = defaultUrlTemplate;
        this.defaultAttribution = defaultAttribution;
        this.requiresApiKey = requiresApiKey;
    }

    public String defaultUrlTemplate() {
        return defaultUrlTemplate;
    }

    public String defaultAttribution() {
        return defaultAttribution;
    }

    /**
     * Whether this provider can't function without an operator-supplied API key.
     * {@link #CUSTOM} returns {@code false} — the operator may build a URL template that
     * doesn't reference {@code {k}}.
     */
    public boolean requiresApiKey() {
        return requiresApiKey;
    }
}
