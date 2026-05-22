/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizTestSection(int id, int testId, String title, String description, int position) {

    public static RowMapping<QuizTestSection> map() {
        return row -> new QuizTestSection(
                row.getInt("id"),
                row.getInt("test_id"),
                row.getString("title"),
                row.getString("description"),
                row.getInt("position"));
    }
}
