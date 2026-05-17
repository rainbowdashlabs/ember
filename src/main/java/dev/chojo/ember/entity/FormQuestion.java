/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record FormQuestion(
        int id,
        int formId,
        int position,
        QuestionType questionType,
        String title,
        String description,
        boolean required,
        boolean shuffle,
        String config) {

    public static RowMapping<FormQuestion> map() {
        return row -> new FormQuestion(
                row.getInt("id"),
                row.getInt("form_id"),
                row.getInt("position"),
                QuestionType.valueOf(row.getString("question_type")),
                row.getString("title"),
                row.getString("description"),
                row.getBoolean("required"),
                row.getBoolean("shuffle"),
                row.getString("config"));
    }

    public enum QuestionType {
        CHOICE,
        TEXT,
        RATING,
        DATE,
        RANKING,
        LIKERT
    }
}
