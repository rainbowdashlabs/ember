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
 * @param id               unique question identifier
 * @param formId           the form this question belongs to
 * @param position         display order position (0-based)
 * @param formQuestionType the type of question (CHOICE, TEXT, RATING, DATE, RANKING, LIKERT)
 * @param title            the question text shown to respondents
 * @param description      optional additional description or instructions
 * @param required         whether an answer is mandatory
 * @param shuffle          whether answer options should be randomized
 * @param config           type-specific configuration
 */
public record FormQuestion(
        int id,
        int formId,
        int position,
        FormQuestionType formQuestionType,
        String title,
        String description,
        boolean required,
        boolean shuffle,
        FormQuestionConfig config) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<FormQuestion> map() {
        return row -> {
            var type = row.getEnum("question_type", FormQuestionType.class);
            return new FormQuestion(
                    row.getInt("id"),
                    row.getInt("form_id"),
                    row.getInt("position"),
                    type,
                    row.getString("title"),
                    row.getString("description"),
                    row.getBoolean("required"),
                    row.getBoolean("shuffle"),
                    FormQuestionConfig.parse(type, row.getString("config")));
        };
    }
}
