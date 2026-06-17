/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record QuizCatalog(
        int id,
        int stationId,
        String name,
        String description,
        boolean trainingEnabled,
        boolean publicRender,
        Instant createdAt,
        Instant updatedAt) {

    public static RowMapping<QuizCatalog> map() {
        return row -> new QuizCatalog(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getBoolean("training_enabled"),
                row.getBoolean("public_render"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("updated_at", INSTANT_TIMESTAMP));
    }
}
