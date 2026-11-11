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

    /**
     * Whether any reminder, by hand or automatic, went out to the station's manager at or after the given
     * moment. A reminder written down as skipped did not go out and does not count.
     *
     * @param stationId the station
     * @param since     the moment to look back to
     * @return {@code true} when one went out since then
     */
    public boolean sentSince(int stationId, Instant since) {
        return query("""
                SELECT EXISTS (
                    SELECT 1 FROM station_manager_reminder
                    WHERE station_id = :station_id
                      AND sent_at >= :since
                ) AS sent;""")
                .single(call().bind("station_id", stationId).bind("since", since, INSTANT_TIMESTAMP))
                .map(row -> row.getBoolean("sent"))
                .first()
                .orElse(false);
    }

    /**
     * Turns the automatic reminder of the station's current wait into one that did not go out, for a
     * reminder whose mail turned out not to be needed after it was written down.
     *
     * @param stationId the station
     * @param trigger   the automatic reminder
     * @return {@code true} when a reminder was changed
     */
    public boolean markNotSent(int stationId, ReminderTrigger trigger) {
        return query("""
                UPDATE station_manager_reminder r
                SET sent_at = NULL
                FROM station s
                WHERE s.id = r.station_id
                  AND r.station_id = :station_id
                  AND r.trigger = :trigger
                  AND r.waiting_since = s.waiting_since;""")
                .single(call().bind("station_id", stationId).bind("trigger", trigger))
                .update()
                .changed();
    }

    /**
     * Whether the newest mail queued for an address came back refused: a hard bounce, a block or a spam
     * report from the provider. Writing to it again would only add another mail that does not arrive.
     *
     * @param address the address, compared without regard to case
     * @return {@code true} when the newest mail to it was refused
     */
    public boolean newestMailRefused(String address) {
        return query("""
                SELECT delivery_status IN ('HARD_BOUNCE', 'BLOCKED', 'SPAM') AS refused
                FROM email_queue
                WHERE lower(recipient) = lower(:address)
                ORDER BY created_at DESC, id DESC
                LIMIT 1;""")
                .single(call().bind("address", address))
                .map(row -> row.getBoolean("refused"))
                .first()
                .orElse(false);
    }
}
