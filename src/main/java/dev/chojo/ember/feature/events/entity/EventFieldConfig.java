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
 * Configuration for an event custom field, stored as JSONB.
 *
 * @param options          selectable values for {@code ENUM}-type fields
 * @param groupId          referenced member group for {@code *_OF_GROUP} fields
 * @param userType         referenced user type for {@code *_OF_TYPE} fields
 * @param tagId            referenced user tag for {@code *_OF_TAG} fields
 * @param selfRegistration when {@code true}, station members can add or remove themselves
 *                         on a {@code MEMBER_*} field without the edit-event permission
 * @param width            how much of a row the field takes when the form is drawn, which is the
 *                         station's own layout choice and means nothing to the server
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventFieldConfig(
        List<String> options,
        Integer groupId,
        StationUserType userType,
        Integer tagId,
        boolean selfRegistration,
        String width) {
    private static final Logger log = getLogger(EventFieldConfig.class);
    private static final ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;
    private static final EventFieldConfig EMPTY = new EventFieldConfig(null, null, null, null, false, null);

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
