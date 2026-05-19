/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

public record StationMailConfig(
        int stationId,
        MailProviderType provider,
        String smtpHost,
        int smtpPort,
        boolean smtpSsl,
        String smtpUser,
        String smtpPassword,
        String senderAddress,
        String senderName,
        String apiKey,
        String providerName,
        String providerUrl,
        int dailyLimit,
        int monthlyLimit) {
    public static RowMapping<StationMailConfig> map() {
        return row -> new StationMailConfig(
                row.getInt("station_id"),
                row.getEnum("provider", MailProviderType.class),
                row.getString("smtp_host"),
                row.getInt("smtp_port"),
                row.getBoolean("smtp_ssl"),
                row.getString("smtp_user"),
                row.getString("smtp_password"),
                row.getString("sender_address"),
                row.getString("sender_name"),
                row.getString("api_key"),
                row.getString("provider_name"),
                row.getString("provider_url"),
                row.getInt("daily_limit"),
                row.getInt("monthly_limit"));
    }

    public boolean isConfigured() {
        return provider != MailProviderType.NONE;
    }
}
