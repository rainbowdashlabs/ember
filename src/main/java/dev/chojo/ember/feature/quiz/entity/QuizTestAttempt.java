/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record QuizTestAttempt(
        int id,
        int testId,
        int memberId,
        AttemptStatus status,
        Instant startedAt,
        Instant submittedAt,
        Instant gradedAt,
        Integer gradedBy,
        int totalPoints,
        int maxPoints) {

    public static RowMapping<QuizTestAttempt> map() {
        return row -> new QuizTestAttempt(
                row.getInt("id"),
                row.getInt("test_id"),
                row.getInt("member_id"),
                AttemptStatus.valueOf(row.getString("status")),
                row.get("started_at", INSTANT_TIMESTAMP),
                row.get("submitted_at", INSTANT_TIMESTAMP),
                row.get("graded_at", INSTANT_TIMESTAMP),
                row.getObject("graded_by", Integer.class),
                row.getInt("total_points"),
                row.getInt("max_points"));
    }
}
