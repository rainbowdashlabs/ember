/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * How many answers a question of a form holds, which is what removing it would throw away.
 *
 * @param questionId the question
 * @param answers    how many responses answered it
 */
public record QuestionAnswerCount(int questionId, int answers) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<QuestionAnswerCount> map() {
        return row -> new QuestionAnswerCount(row.getInt("question_id"), row.getInt("answers"));
    }
}
