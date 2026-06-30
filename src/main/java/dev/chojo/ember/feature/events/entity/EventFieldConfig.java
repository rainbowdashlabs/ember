/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.api.auth.StationUserType;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Configuration for an event custom field, stored as JSONB.
 *
 * @param options          selectable values for {@code ENUM}-type fields
 * @param groupId          referenced member group for {@code *_OF_GROUP} fields
 * @param userType         referenced user type for {@code *_OF_TYPE} fields
 * @param tagId            referenced user tag for {@code *_OF_TAG} fields
 * @param selfRegistration when {@code true}, station members can add or remove themselves
 *                         on a {@code MEMBER_*} field without the edit-event permission
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventFieldConfig(
        List<String> options, Integer groupId, StationUserType userType, Integer tagId, boolean selfRegistration) {
    private static final Logger log = getLogger(EventFieldConfig.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();
    private static final EventFieldConfig EMPTY = new EventFieldConfig(null, null, null, null, false);

    public static EventFieldConfig empty() {
        return EMPTY;
    }

    public static EventFieldConfig parse(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return EMPTY;
        try {
            return MAPPER.readValue(json, EventFieldConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse event field config: {}", json, e);
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
