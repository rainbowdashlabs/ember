/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface BoardFieldConfig {
    Logger log = getLogger(BoardFieldConfig.class);
    ObjectMapper MAPPER = Json.CONFIG_MAPPER;

    static BoardFieldConfig parse(BoardFieldType fieldType, String json) {
        if (json == null || json.isBlank()) return empty(fieldType);
        try {
            return MAPPER.readValue(json, fieldType.configClass());
        } catch (Exception e) {
            log.error("Failed to parse board field config for type {}: {}", fieldType, json, e);
            return empty(fieldType);
        }
    }

    /**
     * Binds settings that arrived as an object rather than as text.
     *
     * <p>Which record they are depends on the field type standing next to them, so they cannot be
     * bound while the request is read. Carrying them this far as a tree rather than as JSON text
     * spares them a trip through the serialiser and back that could only lose something.
     */
    static BoardFieldConfig parse(BoardFieldType fieldType, JsonNode node) {
        if (node == null || node.isNull()) return empty(fieldType);
        try {
            return MAPPER.treeToValue(node, fieldType.configClass());
        } catch (Exception e) {
            log.error("Failed to read board field config for type {}: {}", fieldType, node, e);
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
