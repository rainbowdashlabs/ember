/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.entity;

import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration options for a profile field, parsed from JSON.
 *
 * @param required       whether the field must be filled
 * @param description    a sentence saying what the question is after, shown under its label. A
 *                       field name has to be short enough for a table column, which leaves no room
 *                       to say what counts as an answer, and the person filling it in is the one
 *                       who needs that said.
 * @param readonly       whether the field is read-only for non-managers
 * @param notifyOnChange whether changes to this field require manager acknowledgement
 * @param overview       whether the field is shown in the member overview table
 * @param options        the list of allowed values for ENUM fields
 * @param defaultValue   the default value for new members
 * @param computed       whether this field is computed from another field
 * @param sourceField    the source field name for computed fields
 * @param ageMode        the age calculation mode (e.g. for AGE-type fields)
 * @param groupId        the group a field of GROUP scope belongs to, null for every other scope.
 *                       A field of that scope is only ever shown at its group, so without this it
 *                       belongs nowhere and appears nowhere.
 * @param width          how much of a row the field takes: {@code full}, {@code half} or
 *                       {@code third}. Null is the same as full. Fields narrower than a row stand
 *                       beside each other, which is what turns a column of thirty boxes into
 *                       something that can be read.
 */
public record ProfileFieldConfig(
        boolean required,
        String description,
        boolean readonly,
        boolean notifyOnChange,
        boolean overview,
        List<String> options,
        Object defaultValue,
        boolean computed,
        String sourceField,
        String ageMode,
        Integer groupId,
        String width) {
    private static final Logger log = getLogger(ProfileFieldConfig.class);
    private static final ObjectMapper MAPPER = Json.CONFIG_MAPPER;
    private static final ProfileFieldConfig EMPTY =
            new ProfileFieldConfig(false, null, false, false, false, null, null, false, null, null, null, null);

    /**
     * The settings of a field that names none.
     */
    public static ProfileFieldConfig empty() {
        return EMPTY;
    }

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

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
