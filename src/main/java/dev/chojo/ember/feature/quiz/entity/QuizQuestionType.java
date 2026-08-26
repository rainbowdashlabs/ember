/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import io.javalin.openapi.OpenApiName;
import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@OpenApiName("QuizQuestionType")
public enum QuizQuestionType {
    MULTIPLE_CHOICE("multiple_choice", QuestionConfig.MultipleChoice.class, QuizAnswerValue.MultipleChoice.class),
    FILL_IN_THE_BLANK("fill_blank", QuestionConfig.FillInTheBlank.class, QuizAnswerValue.FillInTheBlank.class),
    FREE_ANSWER("free_answer", QuestionConfig.FreeAnswer.class, QuizAnswerValue.FreeAnswer.class),
    CONNECT("connect", QuestionConfig.Connect.class, QuizAnswerValue.Connect.class),
    IMAGE_TEXT("image_text", QuestionConfig.ImageText.class, QuizAnswerValue.ImageText.class),
    TRUE_FALSE("true_false", QuestionConfig.TrueFalse.class, QuizAnswerValue.TrueFalse.class),
    ORDERING("ordering", QuestionConfig.Ordering.class, QuizAnswerValue.Ordering.class),
    ENUMERATION("enumeration", QuestionConfig.Enumeration.class, QuizAnswerValue.Enumeration.class);

    private static final Logger log = getLogger(QuizQuestionType.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final String promptFile;
    private final Class<? extends QuestionConfig> configClass;
    private final Class<? extends QuizAnswerValue> answerClass;

    QuizQuestionType(
            String promptFile,
            Class<? extends QuestionConfig> configClass,
            Class<? extends QuizAnswerValue> answerClass) {
        this.promptFile = promptFile;
        this.configClass = configClass;
        this.answerClass = answerClass;
    }

    public String promptFile() {
        return promptFile;
    }

    /**
     * Returns the {@link QuizAnswerValue} variant that answers to questions of this
     * type deserialize into.
     */
    public Class<? extends QuizAnswerValue> answerClass() {
        return answerClass;
    }

    /**
     * Parses a JSON config string into the typed config record for this question type. A payload
     * that cannot be read falls back to {@link QuestionConfig.Unknown}, because this reads back
     * columns written by older versions and one stale row must not take out the whole catalog.
     */
    public QuestionConfig parseConfig(String configStr) {
        return readConfig(configStr).orElseGet(QuestionConfig.Unknown::new);
    }

    /**
     * Reads a JSON config the way {@link #parseConfig(String)} does, but reports a payload that
     * does not fit this type instead of quietly emptying it. Inbound files use this, where a
     * config that cannot be read has to be named back to the person importing it.
     *
     * @return the parsed config, or empty when the payload does not fit this type
     */
    public Optional<QuestionConfig> readConfig(String configStr) {
        if (configStr == null || configStr.isBlank()) return Optional.of(new QuestionConfig.Unknown());
        try {
            return Optional.of(MAPPER.readValue(configStr, configClass));
        } catch (Exception e) {
            log.error("Failed to parse config {} for type {}: {}", this, configClass, e.getMessage(), e);
            return Optional.empty();
        }
    }
}
