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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
     * The option keys this answer names: the options picked, the options ranked, or the statements
     * rated. Empty for the kinds of answer that name no option.
     */
    default Set<String> optionKeys() {
        return Set.of();
    }

    /**
     * Whether this answer says nothing: no option and no text picked, no text written, no star given,
     * no date set, nothing ranked or no statement rated.
     *
     * <p>The fill screens send an answer of the right shape for every question, answered or not, so
     * this is the one place that decides what counts as not answered. Empty answers are dropped before
     * anything is checked or stored.
     *
     * @return whether the answer is empty
     */
    boolean isEmpty();

    /**
     * This answer as it reads once the given options no longer exist.
     *
     * @param removed the keys of the options that are gone
     * @return the answer without them, or empty where nothing of it is left
     */
    default Optional<FormAnswerValue> withoutOptions(Set<String> removed) {
        return Optional.of(this);
    }

    private static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Selected option keys + optional other text.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Choice(List<String> selected, String other) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return (selected == null || selected.isEmpty()) && blank(other);
        }

        @Override
        public Set<String> optionKeys() {
            return selected == null ? Set.of() : Set.copyOf(selected);
        }

        /**
         * A choice with none of its options left and no "other" text says nothing any more, so it is
         * gone as a whole.
         */
        @Override
        public Optional<FormAnswerValue> withoutOptions(Set<String> removed) {
            var kept = selected == null
                    ? List.<String>of()
                    : selected.stream().filter(key -> !removed.contains(key)).toList();
            if (kept.isEmpty() && (other == null || other.isBlank())) return Optional.empty();
            return Optional.of(new Choice(kept, other));
        }
    }

    /**
     * Free text answer.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Text(String text) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return blank(text);
        }
    }

    /**
     * Numeric rating.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Rating(int rating) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return rating < 1;
        }
    }

    /**
     * Date answer.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DateValue(String date) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return blank(date);
        }
    }

    /**
     * Ordered ranking.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Ranking(List<String> order) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return order == null || order.isEmpty();
        }

        @Override
        public Set<String> optionKeys() {
            return order == null ? Set.of() : Set.copyOf(order);
        }

        @Override
        public Optional<FormAnswerValue> withoutOptions(Set<String> removed) {
            var kept = order == null
                    ? List.<String>of()
                    : order.stream().filter(key -> !removed.contains(key)).toList();
            return kept.isEmpty() ? Optional.empty() : Optional.of(new Ranking(kept));
        }
    }

    /**
     * Likert scale ratings keyed by statement key.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Likert(Map<String, Integer> ratings) implements FormAnswerValue {
        @Override
        public boolean isEmpty() {
            return ratings == null || ratings.isEmpty();
        }

        @Override
        public Set<String> optionKeys() {
            return ratings == null ? Set.of() : Set.copyOf(ratings.keySet());
        }

        @Override
        public Optional<FormAnswerValue> withoutOptions(Set<String> removed) {
            var kept = new LinkedHashMap<String, Integer>();
            if (ratings != null) {
                ratings.forEach((key, rating) -> {
                    if (!removed.contains(key)) kept.put(key, rating);
                });
            }
            return kept.isEmpty() ? Optional.empty() : Optional.of(new Likert(kept));
        }
    }
}
