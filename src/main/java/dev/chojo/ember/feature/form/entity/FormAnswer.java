/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.form.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Represents a single answer to a form question within a response.
 *
 * @param id         unique answer identifier
 * @param responseId the response this answer belongs to
 * @param questionId the question this answer addresses
 * @param value      the answer value stored as a JSON string
 */
public record FormAnswer(int id, int responseId, int questionId, String value) {
    /** Creates a row mapping for database result set conversion. */
    public static RowMapping<FormAnswer> map() {
        return row -> new FormAnswer(
                row.getInt("id"), row.getInt("response_id"), row.getInt("question_id"), row.getString("value"));
    }
}
