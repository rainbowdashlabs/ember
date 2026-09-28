/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

/**
 * One question of a form as the editor saves it.
 *
 * @param id               the question this entry updates, or {@code null} for a question that is new
 * @param pageKey          the key of the page the question stands on, or {@code null} for the first page
 * @param formQuestionType the type of question
 * @param title            the question text
 * @param description      optional description
 * @param required         whether an answer is mandatory
 * @param shuffle          whether answer options should be randomized
 * @param config           type-specific configuration as JSON
 */
public record QuestionEntry(
        Integer id,
        String pageKey,
        FormQuestionType formQuestionType,
        String title,
        String description,
        boolean required,
        boolean shuffle,
        FormQuestionConfig config) {}
