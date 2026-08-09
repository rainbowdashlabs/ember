/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * Typed answer payload for {@code quiz_test_answer.answer}, one variant per
 * {@link QuizQuestionType}. The question type is reachable through the answer's
 * question, so the JSON carries no discriminator and keeps exactly the shape the
 * clients have been submitting.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public sealed interface QuizAnswerValue {

    /**
     * Shared Jackson mapper for the answer variants.
     */
    ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    /**
     * Parses a submitted answer payload into the variant of the given question type.
     *
     * @param quizQuestionType the type of the answered question
     * @param json             the submitted JSON payload
     * @return the parsed answer, or {@code null} for an absent payload
     * @throws IllegalArgumentException if the payload does not fit the question type
     */
    static QuizAnswerValue parse(QuizQuestionType quizQuestionType, String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, quizQuestionType.answerClass());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid quiz answer payload for question type " + quizQuestionType, e);
        }
    }

    /**
     * Serialises this answer to the JSONB form of {@code quiz_test_answer.answer}.
     */
    default String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize quiz answer", e);
        }
    }

    /**
     * Answer to a {@link QuizQuestionType#MULTIPLE_CHOICE} question.
     *
     * @param selected indices of the ticked options
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MultipleChoice(List<Integer> selected) implements QuizAnswerValue {}

    /**
     * Answer to a {@link QuizQuestionType#TRUE_FALSE} question.
     *
     * @param value the chosen truth value
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TrueFalse(boolean value) implements QuizAnswerValue {}

    /**
     * Answer to a {@link QuizQuestionType#CONNECT} question.
     *
     * @param pairs left-hand index to the chosen right-hand text
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Connect(Map<String, String> pairs) implements QuizAnswerValue {}

    /**
     * Answer to a {@link QuizQuestionType#ORDERING} question.
     *
     * @param order the configured item indices in the order the candidate put them
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Ordering(List<Integer> order) implements QuizAnswerValue {}

    /**
     * Answer to a {@link QuizQuestionType#FILL_IN_THE_BLANK} question. Newer clients
     * fill {@code gaps}; {@code text} is the single-blank form older rows still use.
     *
     * @param gaps gap index to the filled-in word
     * @param text the legacy single-gap answer
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FillInTheBlank(Map<String, String> gaps, String text) implements QuizAnswerValue {}

    /**
     * Answer to an {@link QuizQuestionType#ENUMERATION} question.
     *
     * @param items the listed terms
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Enumeration(List<String> items) implements QuizAnswerValue {}

    /**
     * Answer to a {@link QuizQuestionType#FREE_ANSWER} question.
     *
     * @param text the written answer
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FreeAnswer(String text) implements QuizAnswerValue {}

    /**
     * Answer to an {@link QuizQuestionType#IMAGE_TEXT} question.
     *
     * @param text the description of the shown image
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ImageText(String text) implements QuizAnswerValue {}
}
