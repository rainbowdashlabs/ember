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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    ObjectMapper MAPPER = Json.EMPTY_TOLERANT_CONFIG_MAPPER;

    /**
     * Parses a JSON string into the appropriate config for the given question type. The
     * {@code questionType} discriminator is stamped from the given type before parsing, so
     * configs stored without one - or with a stale value - resolve to the type the question
     * row declares.
     */
    static FormQuestionConfig parse(FormQuestionType formQuestionType, String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) return new Unknown();
        try {
            if (!(MAPPER.readTree(json) instanceof ObjectNode node)) return new Unknown();
            node.put("questionType", formQuestionType.name());
            return MAPPER.treeToValue(node, FormQuestionConfig.class);
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
     * The options an answer picks from: the options of a choice or ranking question, the statements of
     * a Likert grid, and nothing for any other kind.
     *
     * <p>Answers name an option by its key, never by where it stands, so reordering or renaming the
     * options of a question leaves every stored answer meaning what it meant.
     */
    default List<Option> keyedOptions() {
        return List.of();
    }

    /**
     * The keys of {@link #keyedOptions()}, in their order.
     */
    default Set<String> optionKeys() {
        var keys = new LinkedHashSet<String>();
        for (var option : keyedOptions()) keys.add(option.key());
        return keys;
    }

    /**
     * Whether every option carries a key and no two carry the same one.
     */
    default boolean hasDistinctOptionKeys() {
        var options = keyedOptions();
        return options.stream()
                        .allMatch(
                                option -> option.key() != null && !option.key().isBlank())
                && optionKeys().size() == options.size();
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
     * One option of a choice or ranking question, or one statement of a Likert grid.
     *
     * @param key   short text that names the option for good; made when the option is added and never changed
     * @param label what the option says, which the editor may change at any time
     */
    record Option(String key, String label) {
        /**
         * Options with the given labels, keyed by their position the way the options that existed
         * before keys were given theirs: {@code o0}, {@code o1} and so on. For options written once in
         * code, such as demo data; the editor gives every new option a random key.
         */
        public static List<Option> numbered(String... labels) {
            var options = new ArrayList<Option>(labels.length);
            for (int i = 0; i < labels.length; i++) options.add(new Option("o" + i, labels[i]));
            return options;
        }
    }

    /**
     * Choice question: options with optional multi-select, dropdown, and "other" field.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Choice(
            List<Option> options,
            @Nullable Boolean multiSelect,
            @Nullable Boolean dropdown,
            @Nullable Boolean allowOther,
            @Nullable MultiLimitType multiLimitType,
            @Nullable Integer multiLimit)
            implements FormQuestionConfig {
        @Override
        public List<Option> keyedOptions() {
            return options == null ? List.of() : options;
        }

        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.ChoiceAnswer(List<String> selected, String other))) {
                return List.of("Expected choice answer");
            }
            var errors = new ArrayList<String>();
            boolean hasOther = other != null && !other.isBlank();
            var picked = selected == null ? List.<String>of() : selected;
            if (picked.isEmpty() && !hasOther) return List.of("No options selected");
            var keys = optionKeys();
            for (var key : picked) {
                if (!keys.contains(key)) errors.add("Unknown option: " + key);
            }
            if (new HashSet<>(picked).size() != picked.size()) errors.add("An option was selected twice");
            if (!TRUE.equals(multiSelect) && picked.size() > 1) {
                errors.add("Only one option can be selected");
            }
            if (TRUE.equals(multiSelect) && multiLimitType != null && multiLimit != null) {
                int size = picked.size() + (hasOther ? 1 : 0);
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
    record Text(@Nullable Boolean longAnswer) implements FormQuestionConfig {}

    /**
     * Rating question with scale and icon.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Rating(@Nullable Integer scale, @Nullable RatingIcon icon) implements FormQuestionConfig {
        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.RatingAnswer(int rating))) return List.of("Expected rating answer");
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
    record Ranking(List<Option> options) implements FormQuestionConfig {
        @Override
        public List<Option> keyedOptions() {
            return options == null ? List.of() : options;
        }

        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.RankingAnswer(List<String> order))) {
                return List.of("Expected ranking answer");
            }
            if (options == null) return List.of();
            if (order == null || order.size() != options.size() || !optionKeys().equals(new HashSet<>(order))) {
                return List.of("Ranking must contain each of the " + options.size() + " options exactly once");
            }
            return List.of();
        }
    }

    /**
     * Likert scale with statements and scale range.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Likert(
            List<Option> statements,
            @Nullable Integer scaleMin,
            @Nullable Integer scaleMax,
            @Nullable List<String> scaleLabels)
            implements FormQuestionConfig {
        @Override
        public List<Option> keyedOptions() {
            return statements == null ? List.of() : statements;
        }

        @Override
        public List<String> validate(FormAnswerValue value) {
            if (!(value instanceof FormAnswerValue.LikertAnswer(Map<String, Integer> ratings)))
                return List.of("Expected likert answer");
            if (ratings == null || ratings.isEmpty()) return List.of("No ratings provided");
            var errors = new ArrayList<String>();
            int min = scaleMin != null ? scaleMin : 1;
            int max = scaleMax != null ? scaleMax : 5;
            var keys = optionKeys();
            for (var entry : ratings.entrySet()) {
                if (!keys.contains(entry.getKey())) {
                    errors.add("Unknown statement: " + entry.getKey());
                } else if (entry.getValue() == null || entry.getValue() < min || entry.getValue() > max) {
                    errors.add("Rating for '" + entry.getKey() + "' must be between " + min + " and " + max);
                }
            }
            return errors;
        }
    }

    /**
     * Fallback for unknown or empty configs. Serializes to an empty object so a fallback is
     * never persisted with a discriminator that no real config type can parse.
     */
    record Unknown() implements FormQuestionConfig {
        @Override
        public String toJson() {
            return "{}";
        }
    }
}
