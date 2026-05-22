/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record QuizCategory(int id, int stationId, String name, String description, int position) {

    public static RowMapping<QuizCategory> map() {
        return row -> new QuizCategory(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("name"),
                row.getString("description"),
                row.getInt("position"));
    }
}
