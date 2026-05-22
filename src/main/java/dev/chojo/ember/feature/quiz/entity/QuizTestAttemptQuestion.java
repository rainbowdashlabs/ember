/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizTestAttemptQuestion(int id, int attemptId, int questionId, Integer sectionId, int position) {

    public static RowMapping<QuizTestAttemptQuestion> map() {
        return row -> new QuizTestAttemptQuestion(
                row.getInt("id"),
                row.getInt("attempt_id"),
                row.getInt("question_id"),
                row.getObject("section_id", Integer.class),
                row.getInt("position"));
    }
}
