/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

/**
 * Supported question types for form questions.
 */
@io.javalin.openapi.OpenApiName("FormQuestionType")
public enum FormQuestionType {
    CHOICE(FormAnswerValue.Choice.class, FormQuestionConfig.Choice.class),
    TEXT(FormAnswerValue.Text.class, FormQuestionConfig.Text.class),
    RATING(FormAnswerValue.Rating.class, FormQuestionConfig.Rating.class),
    DATE(FormAnswerValue.DateValue.class, FormQuestionConfig.Date.class),
    RANKING(FormAnswerValue.Ranking.class, FormQuestionConfig.Ranking.class),
    LIKERT(FormAnswerValue.Likert.class, FormQuestionConfig.Likert.class),
    ;

    private final Class<? extends FormAnswerValue> answerClass;
    private final Class<? extends FormQuestionConfig> questionClass;

    FormQuestionType(Class<? extends FormAnswerValue> answerClass, Class<? extends FormQuestionConfig> questionClass) {
        this.answerClass = answerClass;
        this.questionClass = questionClass;
    }

    public Class<? extends FormAnswerValue> answerClass() {
        return answerClass;
    }

    public Class<? extends FormQuestionConfig> questionClass() {
        return questionClass;
    }
}
