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
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The named JSON mappers for everything that is not the API boundary: storage backend metadata,
 * entity JSONB payloads, files on disk, AI and HTTP payloads. The API boundary has a mapper of its
 * own in the HTTP server, which carries the station-id translation and strict payload settings that
 * must not leak into internal persistence formats.
 *
 * <p>A mapper is expensive to build and safe to share, so a caller picks the preset that fits rather
 * than building its own. A preset that needs one more module is derived with {@code rebuild()}.
 */
public final class Json {

    /** Jackson's defaults: strict about unknown properties and about {@code null} for a primitive. */
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    /**
     * Tolerant of what it reads: an unknown property is skipped and a {@code null} where a primitive
     * is declared falls back to the default. For payloads written by an older or a newer version,
     * stored or received, where a field the record does not know yet is not a reason to fail.
     */
    public static final JsonMapper LENIENT = lenientBuilder().build();

    /** {@link #LENIENT}, writing indented output for files a person reads. */
    public static final JsonMapper PRETTY =
            lenientBuilder().enable(SerializationFeature.INDENT_OUTPUT).build();

    /**
     * The mapper the stored configuration records use.
     *
     * <p>These records are read back from JSONB columns written by older versions, so an unknown
     * property is expected rather than exceptional and a {@code null} where a primitive is declared
     * has to fall back to the default instead of failing the whole read - a single stale column
     * would otherwise take out the feature that reads it. Fields are read directly and getters
     * ignored so a derived accessor cannot leak into the persisted shape.
     */
    public static final JsonMapper CONFIG_MAPPER = configMapperBuilder().build();

    /**
     * {@link #CONFIG_MAPPER} for records that may serialize to nothing at all - a config whose
     * every field is absent. Without this an empty payload is an error rather than {@code {}}.
     */
    public static final JsonMapper EMPTY_TOLERANT_CONFIG_MAPPER = configMapperBuilder()
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

    private static JsonMapper.Builder lenientBuilder() {
        return JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    }

    private static JsonMapper.Builder configMapperBuilder() {
        return lenientBuilder().changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                .withGetterVisibility(JsonAutoDetect.Visibility.NONE));
    }

    private Json() {}
}
