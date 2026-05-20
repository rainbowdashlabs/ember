/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * Mail configuration for a station, supporting SMTP and API-based email providers.
 *
 * @param stationId     the station this configuration belongs to
 * @param provider      the mail provider type
 * @param smtpHost      the SMTP server hostname
 * @param smtpPort      the SMTP server port
 * @param smtpSsl       whether to use SSL for the SMTP connection
 * @param smtpUser      the SMTP authentication username
 * @param smtpPassword  the SMTP authentication password
 * @param senderAddress the sender email address
 * @param senderName    the sender display name
 * @param apiKey        the API key for API-based providers
 * @param providerName  the display name of the mail provider
 * @param providerUrl   the URL of the mail provider
 * @param dailyLimit    the maximum number of emails per day
 * @param monthlyLimit  the maximum number of emails per month
 */
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
    /**
     * Creates a row mapping for database result set conversion.
     *
     * @return a {@link RowMapping} that maps database rows to {@link StationMailConfig} instances
     */
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

    /**
     * Checks whether a mail provider has been configured (i.e., is not {@link MailProviderType#NONE}).
     *
     * @return {@code true} if a provider is configured
     */
    public boolean isConfigured() {
        return provider != MailProviderType.NONE;
    }
}
