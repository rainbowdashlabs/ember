/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Typed answer values for form questions, one record per question type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface FormAnswerValue {
    Logger log = getLogger(FormAnswerValue.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    /** Selected choice indices + optional other text. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Choice(List<Integer> selected, String other) implements FormAnswerValue {}

    /** Free text answer. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Text(String text) implements FormAnswerValue {}

    /** Numeric rating. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Rating(int rating) implements FormAnswerValue {}

    /** Date answer. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DateValue(String date) implements FormAnswerValue {}

    /** Ordered ranking. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Ranking(List<Integer> order) implements FormAnswerValue {}

    /** Likert scale ratings per sub-item. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Likert(Map<String, Integer> ratings) implements FormAnswerValue {}

    /** Parses a JSON string into the appropriate answer value for the given question type. */
    static FormAnswerValue parse(QuestionType questionType, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, questionType.answerClass());
        } catch (Exception e) {
            log.error("Failed to parse form answer for type {}: {}", questionType, json, e);
            return null;
        }
    }

    /** Serializes this answer value to a JSON string. */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }
}
