/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.lang.Boolean.TRUE;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Typed question config records for form questions, parsed per {@link FormQuestionType}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "questionType")
@JsonSubTypes({
    @JsonSubTypes.Type(value = FormQuestionConfig.Choice.class, name = "CHOICE"),
    @JsonSubTypes.Type(value = FormQuestionConfig.Text.class, name = "TEXT"),
    @JsonSubTypes.Type(value = FormQuestionConfig.Rating.class, name = "RATING"),
    @JsonSubTypes.Type(value = FormQuestionConfig.Date.class, name = "DATE"),
    @JsonSubTypes.Type(value = FormQuestionConfig.Ranking.class, name = "RANKING"),
    @JsonSubTypes.Type(value = FormQuestionConfig.Likert.class, name = "LIKERT"),
})
public sealed interface FormQuestionConfig {
    Logger log = getLogger(FormQuestionConfig.class);
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .changeDefaultVisibility(v -> v.withFieldVisibility(JsonAutoDetect.Visibility.ANY)
                    .withGetterVisibility(JsonAutoDetect.Visibility.NONE))
            .build();

    /**
     * Parses a JSON string into the appropriate config for the given question type.
     */
    static FormQuestionConfig parse(FormQuestionType formQuestionType, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new Unknown();
        try {
            return MAPPER.readValue(json, formQuestionType.questionClass());
        } catch (Exception e) {
            log.error("Failed to parse form question config for type {}: {}", formQuestionType, json, e);
            return new Unknown();
        }
    }

    /**
     * Validates the given answer value against this config.
     *
     * @param value the answer value to validate
     * @return list of validation error messages, empty if valid
     */
    default List<String> validate(FormAnswerValue value) {
        return List.of();
    }

    /**
     * Serializes this config to a JSON string.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            return "{}";
        }
    }

    enum MultiLimitType {
        NONE,
        AT_MOST,
        AT_LEAST,
        EXACTLY
    }

    /**
     * Choice question: options with optional multi-select, dropdown, and "other" field.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Choice(
            List<String> options,
            Boolean multiSelect,
            Boolean dropdown,
            Boolean allowOther,
            MultiLimitType multiLimitType,
            Integer multiLimit)
            implements FormQuestionConfig {
        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.Choice(List<Integer> selected, String other))) {
                return List.of("Expected choice answer");
            }
            var errors = new ArrayList<String>();
            if (selected == null || selected.isEmpty()) return List.of("No options selected");
            if (options != null) {
                for (int idx : selected) {
                    if (idx < 0 || idx >= options.size()) errors.add("Invalid option index: " + idx);
                }
            }
            if (!TRUE.equals(multiSelect) && selected.size() > 1) {
                errors.add("Only one option can be selected");
            }
            if (TRUE.equals(multiSelect) && multiLimitType != null && multiLimit != null) {
                int size = selected.size();
                switch (multiLimitType) {
                    case AT_MOST -> {
                        if (size > multiLimit) errors.add("Too many options selected (max %d)".formatted(multiLimit));
                    }
                    case AT_LEAST -> {
                        if (size < multiLimit) errors.add("Too few options selected (min %d)".formatted(multiLimit));
                    }
                    case EXACTLY -> {
                        if (size != multiLimit) errors.add("Exactly %d options must be selected".formatted(multiLimit));
                    }
                    case NONE -> {}
                }
            }
            if (!TRUE.equals(allowOther) && other != null && !other.isBlank()) {
                errors.add("'Other' option is not allowed");
            }
            return errors;
        }
    }

    /**
     * Free text question.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Text(Boolean longAnswer) implements FormQuestionConfig {}

    /**
     * Rating question with scale and icon.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Rating(Integer scale, RatingIcon icon) implements FormQuestionConfig {
        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.Rating(int rating))) return List.of("Expected rating answer");
            int max = scale != null ? scale : 5;
            if (rating < 1 || rating > max) return List.of("Rating must be between 1 and " + max);
            return List.of();
        }

        public enum RatingIcon {
            HEART,
            STAR,
            THUMB_UP,
            NUMBER
        }
    }

    /**
     * Date question (no special config).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Date() implements FormQuestionConfig {}

    /**
     * Ranking question with orderable options.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Ranking(List<String> options) implements FormQuestionConfig {
        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.Ranking(List<Integer> order))) {
                return List.of("Expected ranking answer");
            }
            if (options == null) return List.of();
            if (order == null || order.size() != options.size()) {
                return List.of("Ranking must contain exactly " + options.size() + " items");
            }
            for (int idx : order) {
                if (idx < 0 || idx >= options.size()) return List.of("Invalid ranking index: " + idx);
            }
            return List.of();
        }
    }

    /**
     * Likert scale with statements and scale range.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Likert(List<String> statements, Integer scaleMin, Integer scaleMax, List<String> scaleLabels)
            implements FormQuestionConfig {
        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.Likert(Map<String, Integer> ratings)))
                return List.of("Expected likert answer");
            if (ratings == null || ratings.isEmpty()) return List.of("No ratings provided");
            var errors = new ArrayList<String>();
            int min = scaleMin != null ? scaleMin : 1;
            int max = scaleMax != null ? scaleMax : 5;
            for (var entry : ratings.entrySet()) {
                if (entry.getValue() < min || entry.getValue() > max) {
                    errors.add("Rating for '" + entry.getKey() + "' must be between " + min + " and " + max);
                }
            }
            return errors;
        }
    }

    /**
     * Fallback for unknown or empty configs.
     */
    record Unknown() implements FormQuestionConfig {}
}
