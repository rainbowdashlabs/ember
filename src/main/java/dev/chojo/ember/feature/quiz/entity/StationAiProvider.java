/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public record StationAiProvider(
        int id,
        int stationId,
        AiVendor provider,
        String apiKey,
        @Nullable String model) {

    /**
     * Reads a stored provider, or nothing where the stored provider is none this instance knows,
     * which then counts as no provider kept.
     */
    public static RowMapping<Optional<StationAiProvider>> map() {
        return row -> {
            Optional<AiVendor> provider = AiVendor.fromKey(row.getString("provider"));
            if (provider.isEmpty()) return Optional.empty();
            return Optional.of(new StationAiProvider(
                    row.getInt("id"),
                    row.getInt("station_id"),
                    provider.get(),
                    row.getString("api_key"),
                    row.getString("model")));
        };
    }

    public StationAiProvider withoutKey() {
        return new StationAiProvider(id, stationId, provider, "***", model);
    }
}
