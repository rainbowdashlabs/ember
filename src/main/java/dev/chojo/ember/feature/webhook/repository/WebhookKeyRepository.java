/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.webhook.repository;

import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The per-station keys that authorise webhook reports from outside.
 */
@Singleton
public class WebhookKeyRepository {

    /**
     * The key of a station, if it has one yet.
     */
    public Optional<String> findByStation(int stationId) {
        return query("SELECT key FROM station_webhook_key WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(row -> row.getString("key"))
                .first();
    }

    /**
     * The station a presented key belongs to.
     */
    public Optional<Integer> findStation(String key) {
        return query("SELECT station_id FROM station_webhook_key WHERE key = :key;")
                .single(call().bind("key", key))
                .map(row -> row.getInt("station_id"))
                .first();
    }

    /**
     * Stores the key of a station, replacing whatever it had. Replacing one takes the old address
     * out of service immediately, which is the point of being able to replace it.
     */
    public void store(int stationId, String key) {
        query("""
                INSERT
                INTO
                    station_webhook_key(station_id, key)
                VALUES
                    (:station_id, :key)
                ON CONFLICT (station_id)
                    DO UPDATE
                    SET
                        key        = :key,
                        created_at = now();""").single(call().bind("station_id", stationId).bind("key", key)).insert();
    }
}
