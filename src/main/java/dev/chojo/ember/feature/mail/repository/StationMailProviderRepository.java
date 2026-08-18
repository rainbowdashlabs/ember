/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import dev.chojo.ember.feature.mail.entity.MailChainEntry;
import jakarta.inject.Singleton;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The providers a station falls back to, after the one in its own mail configuration.
 */
@Singleton
public class StationMailProviderRepository {

    private static final String COLUMNS =
            "position, provider, smtp_host, smtp_port, smtp_ssl, smtp_user, smtp_password, api_key, sender_address, sender_name, attempts, daily_limit, provider_name, provider_url";

    /**
     * The fallbacks of a station, in the order they are tried.
     */
    public List<MailChainEntry> findByStation(int stationId) {
        return query("SELECT %s FROM station_mail_provider WHERE station_id = :station_id ORDER BY position;", COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(MailChainEntry.map())
                .all();
    }

    /**
     * Replaces the fallbacks of a station with the supplied order.
     *
     * <p>Written as a whole rather than row by row: the order is the point, and a half-applied
     * order would send mail through a chain nobody asked for.
     */
    public void replace(int stationId, List<MailChainEntry> entries) {
        query("DELETE FROM station_mail_provider WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
        int position = 0;
        for (var entry : entries) {
            if (!entry.isConfigured()) continue;
            insert(stationId, position++, entry);
        }
    }

    private void insert(int stationId, int position, MailChainEntry entry) {
        query("""
                INSERT
                INTO
                    station_mail_provider(
                        station_id, position, provider, smtp_host, smtp_port, smtp_ssl, smtp_user,
                        smtp_password, api_key, sender_address, sender_name, attempts, daily_limit,
                        provider_name, provider_url)
                VALUES
                    (:station_id, :position, :provider, :smtp_host, :smtp_port, :smtp_ssl, :smtp_user,
                     :smtp_password, :api_key, :sender_address, :sender_name, :attempts, :daily_limit,
                     :provider_name, :provider_url);""")
                .single(call().bind("station_id", stationId)
                        .bind("position", position)
                        .bind("provider", entry.provider().name())
                        .bind("smtp_host", entry.smtpHost())
                        .bind("smtp_port", entry.smtpPort())
                        .bind("smtp_ssl", entry.smtpSsl())
                        .bind("smtp_user", entry.smtpUser())
                        .bind("smtp_password", entry.smtpPassword())
                        .bind("api_key", entry.apiKey())
                        .bind("sender_address", entry.senderAddress())
                        .bind("sender_name", entry.senderName())
                        .bind("attempts", Math.max(1, entry.attempts()))
                        .bind("daily_limit", Math.max(0, entry.dailySendLimit()))
                        .bind("provider_name", entry.providerName() == null ? "" : entry.providerName())
                        .bind("provider_url", entry.providerUrl() == null ? "" : entry.providerUrl()))
                .insert();
    }
}
