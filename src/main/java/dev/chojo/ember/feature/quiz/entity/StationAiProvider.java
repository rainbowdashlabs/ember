/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record StationAiProvider(int id, int stationId, String provider, String apiKey, String model) {

    public static RowMapping<StationAiProvider> map() {
        return row -> new StationAiProvider(
                row.getInt("id"),
                row.getInt("station_id"),
                row.getString("provider"),
                row.getString("api_key"),
                row.getString("model"));
    }

    public StationAiProvider withoutKey() {
        return new StationAiProvider(id, stationId, provider, "***", model);
    }
}
