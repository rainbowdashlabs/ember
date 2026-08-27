/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import io.javalin.http.BadRequestResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shared default-configuration JSON mapper for internal (de)serialization - storage
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
     * has to fall back to the default instead of failing the whole read - a single stale column
     * would otherwise take out the feature that reads it. Fields are read directly and getters
     * ignored so a derived accessor cannot leak into the persisted shape.
     */
    public static final ObjectMapper CONFIG_MAPPER = configMapperBuilder().build();

    /**
     * {@link #CONFIG_MAPPER} for records that may serialize to nothing at all - a config whose
     * every field is absent. Without this an empty payload is an error rather than {@code {}}.
     */
    public static final ObjectMapper EMPTY_TOLERANT_CONFIG_MAPPER = configMapperBuilder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    /**
     * Reads text a client sent as the JSON document it claims to be.
     *
     * <p>Anything held in a JSONB column travels through the API as text, and nothing between the
     * browser and the database looks at it. Reading it here is what keeps text that is not a document
     * from reaching the column, where it ends the whole statement in an error the caller cannot act
     * on. A bad request is what it is, so a bad request is what comes back.
     *
     * @param value the text as it arrived, or null where there is none
     * @return the document, or null where there is none
     * @throws BadRequestResponse when the text is not a JSON document
     */
    public static @Nullable JsonNode document(@Nullable String value) {
        if (value == null) return null;
        try {
            return MAPPER.readTree(value);
        } catch (JacksonException notADocument) {
            throw new BadRequestResponse("The value is not a valid answer for this field");
        }
    }

    private static JsonMapper.Builder configMapperBuilder() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                        .withGetterVisibility(JsonAutoDetect.Visibility.NONE));
    }

    private Json() {}
}
