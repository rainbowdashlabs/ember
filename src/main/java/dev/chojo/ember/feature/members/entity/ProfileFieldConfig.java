/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public record ProfileFieldConfig(
        boolean required,
        boolean readonly,
        boolean notifyOnChange,
        boolean overview,
        List<String> options,
        Object defaultValue,
        boolean computed,
        String sourceField,
        String ageMode) {
    private static final Logger log = getLogger(ProfileFieldConfig.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();
    private static final ProfileFieldConfig EMPTY =
            new ProfileFieldConfig(false, false, false, false, null, null, false, null, null);

    public static ProfileFieldConfig parse(String json) {
        if (json == null || json.isBlank()) return EMPTY;
        try {
            return MAPPER.readValue(json, ProfileFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse profile field config: {}", json, e);
            return EMPTY;
        }
    }
}
