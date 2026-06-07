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
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface BoardFieldValue {
    Logger log = getLogger(BoardFieldValue.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    record StringValue(String value) implements BoardFieldValue {}

    record NumberValue(double value) implements BoardFieldValue {}

    record BooleanValue(boolean value) implements BoardFieldValue {}

    record EnumValue(String value) implements BoardFieldValue {}

    record DateValue(String value) implements BoardFieldValue {}

    record LaneAssignee(int memberId) implements BoardFieldValue {}

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
}
