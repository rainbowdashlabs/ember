/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizTestSectionSource(int id, int sectionId, int catalogId, Integer categoryId, int questionCount) {

    public static RowMapping<QuizTestSectionSource> map() {
        return row -> new QuizTestSectionSource(
                row.getInt("id"),
                row.getInt("section_id"),
                row.getInt("catalog_id"),
                row.getObject("category_id", Integer.class),
                row.getInt("question_count"));
    }
}
