/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class AiProviderRepository {

    public List<StationAiProvider> findByStation(int stationId) {
        return query("SELECT * FROM station_ai_provider WHERE station_id = :station_id ORDER BY provider;")
                .single(call().bind("station_id", stationId))
                .map(StationAiProvider.map())
                .all();
    }

    public Optional<StationAiProvider> findByProvider(int stationId, String provider) {
        return query("SELECT * FROM station_ai_provider WHERE station_id = :station_id AND provider = :provider;")
                .single(call().bind("station_id", stationId).bind("provider", provider))
                .map(StationAiProvider.map())
                .first();
    }

    public void upsert(int stationId, String provider, String apiKey, String model) {
        query("""
                INSERT INTO station_ai_provider(station_id, provider, api_key, model)
                VALUES (:station_id, :provider, :api_key, :model)
                ON CONFLICT (station_id, provider)
                DO UPDATE SET api_key = :api_key, model = :model;""")
                .single(call().bind("station_id", stationId)
                        .bind("provider", provider)
                        .bind("api_key", apiKey)
                        .bind("model", model))
                .insert();
    }

    public void delete(int stationId, String provider) {
        query("DELETE FROM station_ai_provider WHERE station_id = :station_id AND provider = :provider;")
                .single(call().bind("station_id", stationId).bind("provider", provider))
                .delete();
    }

    public Optional<String> getPrompt(int stationId) {
        return query("SELECT ai_prompt FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getString("ai_prompt"))
                .first();
    }

    public void setPrompt(int stationId, String prompt) {
        query("UPDATE station SET ai_prompt = :prompt WHERE id = :id;")
                .single(call().bind("id", stationId).bind("prompt", prompt))
                .update();
    }
}
