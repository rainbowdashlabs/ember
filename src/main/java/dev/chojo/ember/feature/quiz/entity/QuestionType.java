/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

public enum QuestionType {
    MULTIPLE_CHOICE("multiple_choice"),
    FILL_IN_THE_BLANK("fill_blank"),
    FREE_ANSWER("free_answer"),
    CONNECT("connect"),
    IMAGE_TEXT("image_text"),
    TRUE_FALSE("true_false"),
    ORDERING("ordering"),
    ENUMERATION("enumeration");

    private final String promptFile;

    QuestionType(String promptFile) {
        this.promptFile = promptFile;
    }

    public String promptFile() {
        return promptFile;
    }
}
