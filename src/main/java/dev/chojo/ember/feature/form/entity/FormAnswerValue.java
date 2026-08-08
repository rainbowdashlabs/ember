/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import dev.chojo.ember.util.Json;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Typed answer values for form questions, one record per question type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FormAnswerValue.Choice.class, name = "CHOICE"),
    @JsonSubTypes.Type(value = FormAnswerValue.Text.class, name = "TEXT"),
    @JsonSubTypes.Type(value = FormAnswerValue.Rating.class, name = "RATING"),
    @JsonSubTypes.Type(value = FormAnswerValue.DateValue.class, name = "DATE"),
    @JsonSubTypes.Type(value = FormAnswerValue.Ranking.class, name = "RANKING"),
    @JsonSubTypes.Type(value = FormAnswerValue.Likert.class, name = "LIKERT"),
})
public sealed interface FormAnswerValue {
    Logger log = getLogger(FormAnswerValue.class);
    ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    /**
     * Parses a JSON string into the appropriate answer value for the given question type.
     */
    static FormAnswerValue parse(FormQuestionType formQuestionType, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, formQuestionType.answerClass());
        } catch (Exception e) {
            log.error("Failed to parse form answer for type {}: {}", formQuestionType, json, e);
            return null;
        }
    }

    /**
     * Serializes this answer value to a JSON string.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Selected choice indices + optional other text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Choice(List<Integer> selected, String other) implements FormAnswerValue {}

    /**
     * Free text answer.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Text(String text) implements FormAnswerValue {}

    /**
     * Numeric rating.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Rating(int rating) implements FormAnswerValue {}

    /**
     * Date answer.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DateValue(String date) implements FormAnswerValue {}

    /**
     * Ordered ranking.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Ranking(List<Integer> order) implements FormAnswerValue {}

    /**
     * Likert scale ratings per sub-item.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Likert(Map<String, Integer> ratings) implements FormAnswerValue {}
}
