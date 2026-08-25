/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A note somebody left on a question while training, saying that something about it is wrong, out
 * of date or ambiguous.
 *
 * <p>The reporter's name travels with the note so whoever maintains the catalog can ask back. It is
 * resolved at read time rather than copied, and stays empty for a member who has since left.
 *
 * @param id           the note's own id, which is what acknowledging it refers to
 * @param questionId   the question the note is about
 * @param reporterName who wrote it, empty when that member is gone
 * @param note         what they said, in their own words
 * @param createdAt    when they said it
 */
public record QuizQuestionReport(int id, int questionId, String reporterName, String note, Instant createdAt) {

    public static RowMapping<QuizQuestionReport> map() {
        return row -> new QuizQuestionReport(
                row.getInt("id"),
                row.getInt("question_id"),
                row.getString("reporter_name") != null ? row.getString("reporter_name") : "",
                row.getString("note"),
                row.get("created_at", INSTANT_TIMESTAMP));
    }
}
