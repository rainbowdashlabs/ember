/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.QuestionSettings;
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

    static BoardFieldConfig parse(FieldType fieldType, String json) {
        if (json == null || json.isBlank()) return empty(fieldType);
        try {
            return MAPPER.readValue(json, empty(fieldType).getClass());
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
    static BoardFieldConfig parse(FieldType fieldType, JsonNode node) {
        if (node == null || node.isNull()) return empty(fieldType);
        try {
            return MAPPER.treeToValue(node, empty(fieldType).getClass());
        } catch (Exception e) {
            log.error("Failed to read board field config for type {}: {}", fieldType, node, e);
            return empty(fieldType);
        }
    }

    /**
     * The settings a new field of this type starts with, which also says which record its settings
     * are read into.
     *
     * @throws IllegalArgumentException for a type a board does not offer
     */
    static BoardFieldConfig empty(FieldType fieldType) {
        return switch (fieldType) {
            case TEXT, NUMBER, BOOLEAN, DATE -> new Simple(false);
            case CHOICE -> new Enum(false, List.of());
            case LANE_ASSIGNEE -> new LaneAssignee(false, 0);
            default -> throw new IllegalArgumentException("A board does not offer " + fieldType);
        };
    }

    boolean required();

    /** What this field says about the question it asks, as the one check reads it. */
    default QuestionSettings settings() {
        return QuestionSettings.required(required());
    }

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    record Simple(boolean required) implements BoardFieldConfig {}

    record Enum(boolean required, List<String> options) implements BoardFieldConfig {
        @Override
        public QuestionSettings settings() {
            return QuestionSettings.required(required).withOptions(options);
        }
    }

    record LaneAssignee(boolean required, int laneId) implements BoardFieldConfig {}
}
