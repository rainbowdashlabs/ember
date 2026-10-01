/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The shared JSON mappers for everything but the API boundary, whose mapper in the HTTP server carries
 * settings that must not leak into stored formats. A preset that needs one more module is derived with
 * {@code rebuild()}.
 */
public final class Json {

    /** Jackson's defaults: strict about unknown properties and about {@code null} for a primitive. */
    public static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Skips unknown properties and reads {@code null} for a primitive as its default, for other versions' payloads. */
    public static final JsonMapper LENIENT = lenientBuilder().build();

    /** {@link #LENIENT} with indented output, for files a person reads. */
    public static final JsonMapper PRETTY =
            lenientBuilder().enable(SerializationFeature.INDENT_OUTPUT).build();

    /**
     * {@link #LENIENT} reading fields rather than getters, for config records in JSONB columns written by
     * older versions, so a derived accessor never leaks into the stored shape.
     */
    public static final JsonMapper CONFIG_MAPPER = configMapperBuilder().build();

    /** {@link #CONFIG_MAPPER} writing a config whose every field is absent as {@code {}} instead of failing. */
    public static final JsonMapper EMPTY_TOLERANT_CONFIG_MAPPER = configMapperBuilder()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    /**
     * Reads client text bound for a JSONB column, so text that is not a document is a bad request rather
     * than a failed statement.
     *
     * @param value   the text as it arrived, or null
     * @param refusal what the caller refuses with when the text is not a document
     * @return the document, or null
     * @throws RefusalResponse when the text is not a JSON document
     */
    public static @Nullable JsonNode document(@Nullable String value, Refusal refusal) {
        if (value == null) return null;
        try {
            return MAPPER.readTree(value);
        } catch (JacksonException notADocument) {
            throw refusal.raise();
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
