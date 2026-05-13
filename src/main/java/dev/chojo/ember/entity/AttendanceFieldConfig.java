/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

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

    public static AttendanceFieldConfig parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return MAPPER.readValue(json, AttendanceFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse attendance field config: {}", json, e);
            return EMPTY;
        }
    }

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
