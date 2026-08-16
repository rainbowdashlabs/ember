/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration for a registration question, stored as JSONB.
 *
 * <p>The names match {@code AttendanceFieldConfig} so the frontend reads every custom field
 * configuration the same way.
 *
 * @param required     whether a registration is refused without an answer
 * @param defaultValue value the form starts with; it becomes the answer only once submitted
 * @param options      selectable values for {@code ENUM} questions
 * @param min          smallest accepted value for {@code NUMBER} questions
 * @param max          largest accepted value for {@code NUMBER} questions
 * @param groupId      referenced member group for {@code *_OF_GROUP} questions
 * @param userType     referenced user type for {@code *_OF_TYPE} questions
 * @param tagId        referenced user tag for {@code *_OF_TAG} questions
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventRegistrationFieldConfig(
        boolean required,
        String defaultValue,
        List<String> options,
        Integer min,
        Integer max,
        Integer groupId,
        StationUserType userType,
        Integer tagId) {
    private static final Logger log = getLogger(EventRegistrationFieldConfig.class);
    private static final ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;
    private static final EventRegistrationFieldConfig EMPTY =
            new EventRegistrationFieldConfig(false, null, null, null, null, null, null, null);

    public static EventRegistrationFieldConfig empty() {
        return EMPTY;
    }

    public static EventRegistrationFieldConfig parse(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return EMPTY;
        try {
            return MAPPER.readValue(json, EventRegistrationFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse event registration field config: {}", json, e);
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
