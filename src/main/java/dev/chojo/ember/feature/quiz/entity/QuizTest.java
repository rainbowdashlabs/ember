/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record QuizTest(
        int id,
        int stationId,
        String title,
        String description,
        TestStatus status,
        Integer timeLimit,
        boolean shuffle,
        boolean forced,
        Instant startAt,
        Instant endAt,
        int createdBy,
        Instant createdAt,
        Instant updatedAt,
        RestrictionMode restrictionMode,
        boolean restricted) {

    public static RowMapping<QuizTest> map() {
        return row -> new QuizTest(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("title"),
                row.getString("description"),
                row.getEnum("status", TestStatus.class),
                row.getObject("time_limit", Integer.class),
                row.getBoolean("shuffle"),
                row.getBoolean("forced"),
                row.get("start_at", INSTANT_TIMESTAMP),
                row.get("end_at", INSTANT_TIMESTAMP),
                row.getInt("created_by"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getEnum("restriction_mode", RestrictionMode.class),
                row.getBoolean("restricted"));
    }
}
