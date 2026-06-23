/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface BoardFieldConfig {
    Logger log = getLogger(BoardFieldConfig.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    static BoardFieldConfig parse(BoardFieldType fieldType, String json) {
        if (json == null || json.isBlank()) return empty(fieldType);
        try {
            return MAPPER.readValue(json, fieldType.configClass());
        } catch (Exception e) {
            log.error("Failed to parse board field config for type {}: {}", fieldType, json, e);
            return empty(fieldType);
        }
    }

    static BoardFieldConfig empty(BoardFieldType fieldType) {
        return switch (fieldType) {
            case STRING, NUMBER, BOOLEAN, DATE -> new Simple(false);
            case ENUM -> new Enum(false, List.of());
            case LANE_ASSIGNEE -> new LaneAssignee(false, 0);
        };
    }

    boolean required();

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    record Simple(boolean required) implements BoardFieldConfig {}

    record Enum(boolean required, List<String> options) implements BoardFieldConfig {}

    record LaneAssignee(boolean required, int laneId) implements BoardFieldConfig {}
}
