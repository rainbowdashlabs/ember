/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import jakarta.inject.Singleton;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The signing secrets a mail provider issued to a station.
 *
 * <p>Not to be confused with the station's own webhook key: that one is ours and travels in the
 * address, while this is the provider's and proves that a report came from it.
 */
@Singleton
public class ProviderSecretRepository {

    public Optional<String> find(int stationId, MailProviderType provider) {
        return query(
                        "SELECT secret FROM station_provider_secret WHERE station_id = :station_id AND provider = :provider;")
                .single(call().bind("station_id", stationId).bind("provider", provider.name()))
                .map(row -> row.getString("secret"))
                .first();
    }

    /**
     * Stores or replaces a secret. An empty value removes it, which is how a station switches the
     * signature check back off.
     */
    public void store(int stationId, MailProviderType provider, String secret) {
        if (secret == null || secret.isBlank()) {
            query("DELETE FROM station_provider_secret WHERE station_id = :station_id AND provider = :provider;")
                    .single(call().bind("station_id", stationId).bind("provider", provider.name()))
                    .delete();
            return;
        }
        query("""
                INSERT
                INTO
                    station_provider_secret(station_id, provider, secret)
                VALUES
                    (:station_id, :provider, :secret)
                ON CONFLICT (station_id, provider)
                    DO UPDATE
                    SET
                        secret     = :secret,
                        updated_at = now();""")
                .single(call().bind("station_id", stationId)
                        .bind("provider", provider.name())
                        .bind("secret", secret))
                .insert();
    }
}
