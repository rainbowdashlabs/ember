/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import org.slf4j.Logger;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.slf4j.LoggerFactory.getLogger;

public enum QuestionType {
    MULTIPLE_CHOICE("multiple_choice", QuestionConfig.MultipleChoice.class),
    FILL_IN_THE_BLANK("fill_blank", QuestionConfig.FillInTheBlank.class),
    FREE_ANSWER("free_answer", QuestionConfig.FreeAnswer.class),
    CONNECT("connect", QuestionConfig.Connect.class),
    IMAGE_TEXT("image_text", QuestionConfig.ImageText.class),
    TRUE_FALSE("true_false", QuestionConfig.TrueFalse.class),
    ORDERING("ordering", QuestionConfig.Ordering.class),
    ENUMERATION("enumeration", QuestionConfig.Enumeration.class);

    private static final Logger log = getLogger(QuestionType.class);
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private final String promptFile;
    private final Class<? extends QuestionConfig> configClass;

    QuestionType(String promptFile, Class<? extends QuestionConfig> configClass) {
        this.promptFile = promptFile;
        this.configClass = configClass;
    }

    public String promptFile() {
        return promptFile;
    }

    public Class<? extends QuestionConfig> configClass() {
        return configClass;
    }

    /**
     * Parses a JSON config string into the typed config record for this question type.
     */
    public QuestionConfig parseConfig(String configStr) {
        if (configStr == null || configStr.isBlank()) return new QuestionConfig.Unknown();
        try {
            return MAPPER.readValue(configStr, configClass);
        } catch (Exception e) {
            log.error("Failed to parse config {} for type {}: {}", this, configClass, e.getMessage(), e);
            return new QuestionConfig.Unknown();
        }
    }
}
