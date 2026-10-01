/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.repository;

import dev.chojo.ember.feature.quiz.entity.StationAiProvider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

@Singleton
public class AiProviderRepository {
    private static final String STATION_AI_PROVIDER_COLUMNS = "id, station_id, provider, api_key, model";

    public List<StationAiProvider> findByStation(int stationId) {
        return query(
                        "SELECT %s FROM station_ai_provider WHERE station_id = :station_id ORDER BY provider;",
                        STATION_AI_PROVIDER_COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(StationAiProvider.map())
                .all();
    }

    public Optional<StationAiProvider> findByProvider(int stationId, String provider) {
        return query(
                        "SELECT %s FROM station_ai_provider WHERE station_id = :station_id AND provider = :provider;",
                        STATION_AI_PROVIDER_COLUMNS)
                .single(call().bind("station_id", stationId).bind("provider", provider))
                .map(StationAiProvider.map())
                .first();
    }

    public void upsert(int stationId, String provider, String apiKey, @Nullable String model) {
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

    /**
     * Every station provider whose key is stored without the given prefix, which is how a key written
     * before encryption at rest is recognised.
     *
     * @param sealedPrefix the prefix every encrypted value starts with
     * @return the providers still holding a plaintext key
     */
    public List<StationAiProvider> findWithoutPrefix(String sealedPrefix) {
        return query("""
                SELECT %s
                FROM station_ai_provider
                WHERE NOT starts_with(api_key, :prefix)
                ORDER BY id;""", STATION_AI_PROVIDER_COLUMNS)
                .single(call().bind("prefix", sealedPrefix))
                .map(StationAiProvider.map())
                .all();
    }

    /**
     * Replaces a provider's key, but only while it still holds the value that was read, so a key a
     * station saved in the meantime is left alone.
     *
     * @param id       the provider row
     * @param expected the key as it was read
     * @param stored   the key to write instead
     * @return whether the key was replaced
     */
    public boolean replaceKeyIfUnchanged(int id, String expected, String stored) {
        return query("""
                UPDATE station_ai_provider
                SET api_key = :stored
                WHERE id = :id AND api_key = :expected;""")
                .single(call().bind("stored", stored).bind("id", id).bind("expected", expected))
                .update()
                .changed();
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
