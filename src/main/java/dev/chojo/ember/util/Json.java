/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared default-configuration JSON mapper for internal (de)serialization — storage
 * backend metadata, entity JSONB payloads, CSV/AI processing, federation version
 * hashing. Deliberately distinct from the API-boundary mapper in the HTTP server,
 * which carries the station-id translation module and strict payload settings that
 * must not leak into internal persistence formats.
 */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * The mapper the stored configuration records use.
     *
     * <p>These records are read back from JSONB columns written by older versions, so an unknown
     * property is expected rather than exceptional and a {@code null} where a primitive is declared
     * has to fall back to the default instead of failing the whole read — a single stale column
     * would otherwise take out the feature that reads it. Fields are read directly and getters
     * ignored so a derived accessor cannot leak into the persisted shape.
     */
    public static final ObjectMapper CONFIG_MAPPER = configMapperBuilder().build();

    /**
     * {@link #CONFIG_MAPPER} for records that may serialize to nothing at all — a config whose
     * every field is absent. Without this an empty payload is an error rather than {@code {}}.
     */
    public static final ObjectMapper EMPTY_TOLERANT_CONFIG_MAPPER = configMapperBuilder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private static JsonMapper.Builder configMapperBuilder() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE));
    }

    private Json() {}
}
