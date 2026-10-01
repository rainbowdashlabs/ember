/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

/**
 * Supported question types for form questions.
 */
public enum FormQuestionType {
    CHOICE(FormAnswerValue.ChoiceAnswer.class, FormQuestionConfig.Choice.class),
    TEXT(FormAnswerValue.TextAnswer.class, FormQuestionConfig.Text.class),
    RATING(FormAnswerValue.RatingAnswer.class, FormQuestionConfig.Rating.class),
    DATE(FormAnswerValue.DateAnswer.class, FormQuestionConfig.Date.class),
    RANKING(FormAnswerValue.RankingAnswer.class, FormQuestionConfig.Ranking.class),
    LIKERT(FormAnswerValue.LikertAnswer.class, FormQuestionConfig.Likert.class),
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

    /**
     * Whether this question type may be used on a form with the given purpose.
     *
     * <p>The whitelist:
     * <ul>
     *   <li>{@link FormPurpose#INTERNAL} - all types.</li>
     *   <li>{@link FormPurpose#CONTACT} - {@link #TEXT}, {@link #CHOICE}, {@link #DATE} only.</li>
     *   <li>{@link FormPurpose#POLL} - all types.</li>
     * </ul>
     *
     * @param purpose the form's purpose
     * @return {@code true} if this type is allowed
     */
    public boolean allowedFor(FormPurpose purpose) {
        return switch (purpose) {
            case INTERNAL, POLL -> true;
            case CONTACT -> this == TEXT || this == CHOICE || this == DATE;
        };
    }
}
