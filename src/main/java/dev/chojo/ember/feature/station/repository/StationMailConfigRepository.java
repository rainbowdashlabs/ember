/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.station.entity.StationMailConfig;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for station mail configuration and per-station email send count tracking.
 */
@Singleton
public class StationMailConfigRepository {

    /**
     * Finds the mail configuration for a station.
     *
     * @param stationId the station ID
     * @return the mail configuration, or empty if none exists
     */
    public Optional<StationMailConfig> findByStation(int stationId) {
        return query("SELECT * FROM station_mail_config WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .map(StationMailConfig.map())
                .first();
    }

    /**
     * Inserts or updates the mail configuration for a station.
     *
     * @param config the mail configuration to upsert
     * @return the persisted mail configuration
     */
    public StationMailConfig upsert(StationMailConfig config) {
        return query("""
                INSERT
                INTO
                    station_mail_config(station_id, provider, smtp_host, smtp_port, smtp_ssl,
                                        smtp_user, smtp_password, sender_address, sender_name, api_key,
                                        provider_name, provider_url, daily_limit, monthly_limit, updated_at)
                VALUES
                    (:station_id, :provider, :smtp_host, :smtp_port, :smtp_ssl,
                     :smtp_user, :smtp_password, :sender_address, :sender_name, :api_key,
                     :provider_name, :provider_url, :daily_limit, :monthly_limit, now())
                ON CONFLICT (station_id)
                    DO UPDATE
                    SET
                        provider       = :provider,
                        smtp_host      = :smtp_host,
                        smtp_port      = :smtp_port,
                        smtp_ssl       = :smtp_ssl,
                        smtp_user      = :smtp_user,
                        smtp_password  = :smtp_password,
                        sender_address = :sender_address,
                        sender_name    = :sender_name,
                        api_key        = :api_key,
                        provider_name  = :provider_name,
                        provider_url   = :provider_url,
                        daily_limit    = :daily_limit,
                        monthly_limit  = :monthly_limit,
                        updated_at     = now()
                RETURNING *;""")
                .single(call().bind("station_id", config.stationId())
                        .bind("provider", config.provider())
                        .bind("smtp_host", config.smtpHost())
                        .bind("smtp_port", config.smtpPort())
                        .bind("smtp_ssl", config.smtpSsl())
                        .bind("smtp_user", config.smtpUser())
                        .bind("smtp_password", config.smtpPassword())
                        .bind("sender_address", config.senderAddress())
                        .bind("sender_name", config.senderName())
                        .bind("api_key", config.apiKey())
                        .bind("provider_name", config.providerName())
                        .bind("provider_url", config.providerUrl())
                        .bind("daily_limit", config.dailyLimit())
                        .bind("monthly_limit", config.monthlyLimit()))
                .map(StationMailConfig.map())
                .first()
                .orElseThrow();
    }

    /**
     * Deletes the mail configuration for a station.
     *
     * @param stationId the station ID
     */
    public void delete(int stationId) {
        query("DELETE FROM station_mail_config WHERE station_id = :station_id;")
                .single(call().bind("station_id", stationId))
                .delete();
    }

    // -- Per-station send counts --

    /**
     * Gets the number of emails sent by a station on a specific day.
     *
     * @param stationId the station ID
     * @param day       the day to query
     * @return the number of emails sent, or 0 if no record exists
     */
    public int getDailyCount(int stationId, LocalDate day) {
        return query("SELECT count FROM station_email_count WHERE station_id = :station_id AND day = :day;")
                .single(call().bind("station_id", stationId).bind("day", day))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * Gets the total number of emails sent by a station during the month of the given date.
     *
     * @param stationId the station ID
     * @param month     any date within the target month
     * @return the total number of emails sent that month
     */
    public int getMonthlyCount(int stationId, LocalDate month) {
        LocalDate firstDay = month.withDayOfMonth(1);
        LocalDate lastDay = month.withDayOfMonth(month.lengthOfMonth());
        return query("""
                SELECT
                    coalesce(sum(count), 0)
                FROM
                    station_email_count
                WHERE station_id = :station_id
                  AND day >= :first
                  AND day <= :last;""")
                .single(call().bind("station_id", stationId)
                        .bind("first", firstDay)
                        .bind("last", lastDay))
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    /**
     * Increments the email send count for a station on a specific day by one.
     *
     * @param stationId the station ID
     * @param day       the day to increment
     */
    public void incrementDailyCount(int stationId, LocalDate day) {
        query("""
                INSERT
                INTO
                    station_email_count(station_id, day, count)
                VALUES
                    (:station_id, :day, 1)
                ON CONFLICT (station_id, day) DO UPDATE SET
                    count = station_email_count.count + 1;""").single(call().bind("station_id", stationId).bind("day", day)).insert();
    }

    /**
     * Deletes email send count records older than the specified number of days.
     *
     * @param keepDays the number of days of history to retain
     */
    public void cleanupOldCounts(int keepDays) {
        query("DELETE FROM station_email_count WHERE day < :cutoff;")
                .single(call().bind("cutoff", LocalDate.now().minusDays(keepDays)))
                .delete();
    }
}
