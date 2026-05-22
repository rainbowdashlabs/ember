/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizTestFrozenQuestion(int id, int testId, int questionId, Integer sectionId, int position) {

    public static RowMapping<QuizTestFrozenQuestion> map() {
        return row -> new QuizTestFrozenQuestion(
                row.getInt("id"),
                row.getInt("test_id"),
                row.getInt("question_id"),
                row.getObject("section_id", Integer.class),
                row.getInt("position"));
    }
}
