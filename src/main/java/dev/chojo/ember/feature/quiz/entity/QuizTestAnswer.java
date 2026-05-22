/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizTestAnswer(
        int id,
        int attemptId,
        int questionId,
        Integer sectionId,
        String answer,
        Integer points,
        boolean graded,
        int position) {

    public static RowMapping<QuizTestAnswer> map() {
        return row -> new QuizTestAnswer(
                row.getInt("id"),
                row.getInt("attempt_id"),
                row.getInt("question_id"),
                row.getObject("section_id", Integer.class),
                row.getString("answer"),
                row.getObject("points", Integer.class),
                row.getBoolean("graded"),
                row.getInt("position"));
    }
}
