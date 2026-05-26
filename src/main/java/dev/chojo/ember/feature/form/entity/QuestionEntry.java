/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

/**
 * Data transfer object for creating questions during a bulk replace operation.
 *
 * @param questionType the type of question
 * @param title        the question text
 * @param description  optional description
 * @param required     whether an answer is mandatory
 * @param shuffle      whether answer options should be randomized
 * @param config       type-specific configuration as JSON
 */
public record QuestionEntry(
        FormQuestion.QuestionType questionType,
        String title,
        String description,
        boolean required,
        boolean shuffle,
        FormQuestionConfig config) {}
