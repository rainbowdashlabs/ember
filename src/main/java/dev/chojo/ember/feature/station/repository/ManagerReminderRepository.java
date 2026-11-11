/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.repository;

import dev.chojo.ember.feature.station.entity.ReminderKind;
import dev.chojo.ember.feature.station.entity.ReminderTrigger;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The reminders sent to the managers of stations that are not set up yet, and what deciding on the next one
 * needs to know.
 *
 * <p>An automatic reminder is written at most once per station, trigger and waiting period: the table's
 * partial unique index refuses a second row, and {@link #record} reports that by returning {@code false}.
 */
@Singleton
public class ManagerReminderRepository {

    /**
     * Holds the station's row until the calling transaction ends, so two reminders for one station are
     * decided one after the other.
     *
     * @param stationId the station
     * @return {@code true} when the station exists
     */
    public boolean lockStation(int stationId) {
        return query("SELECT id FROM station WHERE id = :id FOR UPDATE;")
                .single(call().bind("id", stationId))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    /**
     * Writes a reminder down. The waiting period is taken from the station as it stands, and a station that
     * waits for nobody yet counts as waiting from now.
     *
     * @param stationId the station
     * @param accountId the manager it is written to
     * @param kind      which mail it is
     * @param trigger   what sent it
     * @param sentAt    when it was queued, or {@code null} for an automatic reminder that was skipped
     * @param sentBy    the administrator who sent it by hand, or {@code null} for an automatic one
     * @return {@code false} when this automatic reminder was already written for the waiting period
     */
    public boolean record(
            int stationId,
            int accountId,
            ReminderKind kind,
            ReminderTrigger trigger,
            @Nullable Instant sentAt,
            @Nullable Integer sentBy) {
        return query("""
                INSERT INTO station_manager_reminder(station_id, account_id, kind, trigger, waiting_since, sent_at, sent_by)
                SELECT s.id, :account_id, :kind, :trigger, coalesce(s.waiting_since, now()), :sent_at, :sent_by
                FROM station s
                WHERE s.id = :station_id
                ON CONFLICT DO NOTHING
                RETURNING id;""")
                .single(call().bind("station_id", stationId)
                        .bind("account_id", accountId)
                        .bind("kind", kind)
                        .bind("trigger", trigger)
                        .bind("sent_at", sentAt, INSTANT_TIMESTAMP)
                        .bind("sent_by", sentBy))
                .map(row -> row.getInt("id"))
                .first()
                .isPresent();
    }

    /**
     * Whether an administrator sent the station's manager a reminder by hand at or after the given moment.
     *
     * @param stationId the station
     * @param since     the moment to look back to
     * @return {@code true} when one went out since then
     */
    public boolean sentByHandSince(int stationId, Instant since) {
        return query("""
                SELECT EXISTS (
                    SELECT 1 FROM station_manager_reminder
                    WHERE station_id = :station_id
                      AND trigger = :manual
                      AND sent_at >= :since
                ) AS sent;""")
                .single(call().bind("station_id", stationId)
                        .bind("manual", ReminderTrigger.MANUAL)
                        .bind("since", since, INSTANT_TIMESTAMP))
                .map(row -> row.getBoolean("sent"))
                .first()
                .orElse(false);
    }
}
