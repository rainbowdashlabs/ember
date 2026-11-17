/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.maps.entity;

import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Geocoding configuration persisted as JSON under {@code application_setting.maps_geocoding}.
 *
 * <p>A setting written by an older version may name a provider that is gone and carry an API key
 * and contact address nobody reads any more. Such a setting reads as {@link GeocodingProvider#NONE}
 * with the default server, and the leftover fields are dropped on the next save.
 *
 * @param provider who looks up addresses
 * @param baseUrl  the Photon server to ask, blank for the public one
 */
public record MapsGeocodingConfig(
        GeocodingProvider provider, @Nullable String baseUrl) {

    /** The public Photon server, used whenever the operator names none. */
    public static final String DEFAULT_BASE_URL = "https://photon.komoot.io";

    private static final String SEARCH_PATH = "/api";

    public static final MapsGeocodingConfig DEFAULT = new MapsGeocodingConfig(GeocodingProvider.NONE, DEFAULT_BASE_URL);

    /**
     * Reads a stored setting, tolerating every shape an older version wrote.
     *
     * @param json the stored text
     * @return the setting it holds
     */
    public static MapsGeocodingConfig parse(String json) {
        try {
            JsonNode node = Json.LENIENT.readTree(json);
            var provider = GeocodingProvider.fromStored(textOf(node, "provider"));
            return new MapsGeocodingConfig(provider, textOf(node, "baseUrl"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse MapsGeocodingConfig", e);
        }
    }

    private static @Nullable String textOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    public String toJson() {
        try {
            return Json.MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize MapsGeocodingConfig", e);
        }
    }

    /**
     * The server to ask, without a trailing slash: the configured one, or the public one when none
     * is configured. An address pasted with the search path {@code /api} on its end is read without it,
     * since the search adds that path itself.
     *
     * @return the base address of the Photon server
     */
    public String resolvedBaseUrl() {
        String configured = withoutSuffix(baseUrl == null ? "" : baseUrl.trim(), "/");
        configured = withoutSuffix(withoutSuffix(configured, SEARCH_PATH), "/");
        return configured.isEmpty() ? DEFAULT_BASE_URL : configured;
    }

    private static String withoutSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }
}
