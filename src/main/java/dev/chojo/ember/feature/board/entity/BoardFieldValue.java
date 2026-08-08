/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface BoardFieldValue {
    Logger log = getLogger(BoardFieldValue.class);
    ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    static BoardFieldValue parse(BoardFieldType fieldType, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, fieldType.valueClass());
        } catch (Exception e) {
            log.error("Failed to parse board field value for type {}: {}", fieldType, json, e);
            return null;
        }
    }

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    record StringValue(String value) implements BoardFieldValue {}

    record NumberValue(double value) implements BoardFieldValue {}

    record BooleanValue(boolean value) implements BoardFieldValue {}

    record EnumValue(String value) implements BoardFieldValue {}

    record DateValue(String value) implements BoardFieldValue {}

    record LaneAssignee(int memberId) implements BoardFieldValue {}
}
