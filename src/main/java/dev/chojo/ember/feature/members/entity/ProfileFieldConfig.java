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

/**
 * Configuration options for a profile field, parsed from JSON.
 *
 * @param required        whether the field must be filled
 * @param readonly        whether the field is read-only for non-managers
 * @param notifyOnChange  whether changes to this field require manager acknowledgement
 * @param overview        whether the field is shown in the member overview table
 * @param options         the list of allowed values for ENUM fields
 * @param defaultValue    the default value for new members
 * @param computed        whether this field is computed from another field
 * @param sourceField     the source field name for computed fields
 * @param ageMode         the age calculation mode (e.g. for AGE-type fields)
 */
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

    /**
     * Parses a JSON string into a {@link ProfileFieldConfig}, returning a default empty config on failure.
     *
     * @param json the JSON configuration string, may be null or blank
     * @return the parsed config or a default empty config
     */
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
