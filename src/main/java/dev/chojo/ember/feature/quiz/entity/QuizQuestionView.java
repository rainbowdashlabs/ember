/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

/**
 * Question representation for members without catalog access: the same identity and
 * presentation fields as {@link QuizQuestion}, but with the solution-bearing config
 * replaced by its answer-free {@link QuizQuestionConfigView} projection and the
 * grading and audit fields dropped.
 *
 * @param id               the question id
 * @param catalogId        the owning catalog
 * @param categoryId       the category the question belongs to, or {@code null}
 * @param quizQuestionType the question type
 * @param title            the question text
 * @param description      supplementary text
 * @param imageUrl         an illustrating image, or {@code null}
 * @param points           the achievable points
 * @param position         the sort position inside the catalog
 * @param config           the answer-free config projection
 */
public record QuizQuestionView(
        int id,
        int catalogId,
        Integer categoryId,
        QuizQuestionType quizQuestionType,
        String title,
        String description,
        String imageUrl,
        double points,
        int position,
        QuizQuestionConfigView config) {}
