/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration for an attendance template field, parsed from JSONB storage.
 *
 * @param required     whether the field must be filled in
 * @param groupId      optional member group restriction for this field
 * @param autoAttend   whether members referenced in this field are automatically marked as present
 * @param options      selectable options for choice-type fields
 * @param defaultValue default value to pre-populate when creating a session
 */
public record AttendanceFieldConfig(
        boolean required, Integer groupId, boolean autoAttend, List<String> options, Object defaultValue) {
    private static final Logger log = getLogger(AttendanceFieldConfig.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();
    private static final AttendanceFieldConfig EMPTY = new AttendanceFieldConfig(false, null, false, null, null);

    /**
     * Parses a JSON string into an {@link AttendanceFieldConfig}, returning an empty default on failure.
     *
     * @param json the JSON string to parse, may be {@code null} or blank
     * @return the parsed config or an empty default
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static AttendanceFieldConfig parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return MAPPER.readValue(json, AttendanceFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse attendance field config: {}", json, e);
            return EMPTY;
        }
    }

    /**
     * Checks whether this config specifies a default value.
     *
     * @return {@code true} if a default value is set
     */
    public boolean hasDefaultValue() {
        return defaultValue != null;
    }

    /**
     * Returns the default value as a JSON string suitable for JSONB storage.
     * Handles the __TODAY__ sentinel for date fields.
     */
    public String resolveDefaultValueJson() {
        if (defaultValue == null) return null;
        if (defaultValue instanceof String s) {
            if ("__TODAY__".equals(s)) {
                return "\"" + LocalDate.now() + "\"";
            }
            return "\"" + s + "\"";
        }
        // Booleans and numbers are already valid JSON
        return String.valueOf(defaultValue);
    }
}
