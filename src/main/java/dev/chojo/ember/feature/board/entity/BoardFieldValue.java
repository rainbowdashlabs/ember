/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.util.Json;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import static org.slf4j.LoggerFactory.getLogger;

@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface BoardFieldValue {
    Logger log = getLogger(BoardFieldValue.class);
    ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    static @Nullable BoardFieldValue parse(FieldType fieldType, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, recordOf(fieldType));
        } catch (Exception e) {
            log.error("Failed to parse board field value for type {}: {}", fieldType, json, e);
            return null;
        }
    }

    /**
     * The record a ticket's value of a field of this type is read into.
     *
     * @throws IllegalArgumentException for a type a board does not offer
     */
    private static Class<? extends BoardFieldValue> recordOf(FieldType fieldType) {
        return switch (fieldType) {
            case TEXT -> StringValue.class;
            case NUMBER -> NumberValue.class;
            case BOOLEAN -> BooleanValue.class;
            case CHOICE -> EnumValue.class;
            case DATE -> DateValue.class;
            case LANE_ASSIGNEE -> LaneAssigneeValue.class;
            default -> throw new IllegalArgumentException("A board does not offer " + fieldType);
        };
    }

    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * The value as the plain text the one check measures against the field.
     *
     * @return the text
     */
    String answer();

    record StringValue(String value) implements BoardFieldValue {
        @Override
        public String answer() {
            return value;
        }
    }

    record NumberValue(double value) implements BoardFieldValue {
        @Override
        public String answer() {
            return String.valueOf(value);
        }
    }

    record BooleanValue(boolean value) implements BoardFieldValue {
        @Override
        public String answer() {
            return String.valueOf(value);
        }
    }

    record EnumValue(String value) implements BoardFieldValue {
        @Override
        public String answer() {
            return value;
        }
    }

    /**
     * A calendar day, written as the ISO date it is ({@code 2026-10-01}). Anything else is refused
     * when it is saved; one kept from before that is still shown as it was written.
     */
    record DateValue(String value) implements BoardFieldValue {
        @Override
        public String answer() {
            return value;
        }
    }

    record LaneAssigneeValue(int memberId) implements BoardFieldValue {
        @Override
        public String answer() {
            return String.valueOf(memberId);
        }
    }
}
