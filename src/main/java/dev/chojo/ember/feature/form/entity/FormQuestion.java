/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a question within a form.
 *
 * @param id           unique question identifier
 * @param formId       the form this question belongs to
 * @param position     display order position (0-based)
 * @param questionType the type of question (CHOICE, TEXT, RATING, DATE, RANKING, LIKERT)
 * @param title        the question text shown to respondents
 * @param description  optional additional description or instructions
 * @param required     whether an answer is mandatory
 * @param shuffle      whether answer options should be randomized
 * @param config       type-specific configuration stored as a JSON string
 */
public record FormQuestion(
        int id,
        int formId,
        int position,
        QuestionType questionType,
        String title,
        String description,
        boolean required,
        boolean shuffle,
        FormQuestionConfig config) {

    /**
     * Creates a row mapping for database result set conversion.
     */
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
                FormQuestionConfig.parse(row.getString("config")));
    }

    /**
     * Supported question types for form questions.
     */
    public enum QuestionType {
        CHOICE,
        TEXT,
        RATING,
        DATE,
        RANKING,
        LIKERT
    }
}
