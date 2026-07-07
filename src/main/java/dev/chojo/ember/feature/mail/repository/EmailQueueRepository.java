/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for the email queue, managing enqueuing, fetching, status updates, rate limiting,
 * and cleanup of outbound emails.
 */
@Singleton
public class EmailQueueRepository {

    /**
     * Enqueues an email without a station association (global/system email).
     *
     * @param recipient the recipient email address
     * @param subject   the email subject
     * @param body      the HTML email body
     */
    public void enqueue(String recipient, String subject, String body) {
        enqueue(recipient, subject, body, null);
    }

    /**
     * Enqueues an email, optionally associated with a station for per-station rate limiting.
     *
     * @param recipient the recipient email address
     * @param subject   the email subject
     * @param body      the HTML email body
     * @param stationId the station ID (null for system emails)
     */
    public void enqueue(String recipient, String subject, String body, Integer stationId) {
        query(
                        "INSERT INTO email_queue(recipient, subject, body, station_id) VALUES(:recipient, :subject, :body, :station_id);")
                .single(call().bind("recipient", recipient)
                        .bind("subject", subject)
                        .bind("body", body)
                        .bind("station_id", stationId))
                .insert();
    }

    /**
     * Atomically fetches pending emails and marks them as SENDING to prevent double-processing.
     * Global emails (no station) can be excluded while the instance-wide mail provider is not
     * configured, so they stay queued untouched until an operator sets one up.
     *
     * @param limit         the maximum number of emails to fetch
     * @param includeGlobal whether emails without a station association are fetched
     * @return the list of emails now in SENDING state
     */
    public List<QueuedEmail> fetchPending(int limit, boolean includeGlobal) {
        return query("""
                UPDATE email_queue SET status = 'SENDING'
                WHERE id IN (
                    SELECT id FROM email_queue
                    WHERE status = 'PENDING' AND (:include_global OR station_id IS NOT NULL)
                    ORDER BY created_at LIMIT :limit
                )
                RETURNING id, recipient, subject, body, station_id;""")
                .single(call().bind("limit", limit).bind("include_global", includeGlobal))
                .map(row -> new QueuedEmail(
                        row.getInt("id"),
                        row.getString("recipient"),
                        row.getString("subject"),
                        row.getString("body"),
                        row.getObject("station_id", Integer.class)))
                .all();
    }

    /**
     * Marks an email as successfully sent.
     *
     * @param id the queued email ID
     */
    public void markSent(int id) {
        query("UPDATE email_queue SET status = 'SENT' WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Marks an email as failed to send.
     *
     * @param id the queued email ID
     */
    public void markFailed(int id) {
        query("UPDATE email_queue SET status = 'FAILED' WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Requeues a failed email for another send attempt.
     *
     * @param id the queued email ID
     */
    public void requeue(int id) {
        query("UPDATE email_queue SET status = 'PENDING' WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Returns the number of emails currently pending in the queue.
     *
     * @return the pending email count
     */
    public int pendingCount() {
        return query("SELECT count(*) FROM email_queue WHERE status = 'PENDING';")
                .single()
                .map(row -> row.getInt(1))
                .first()
                .orElse(0);
    }

    /**
     * Gets the number of global emails sent on a given day.
     *
     * @param day the date to query
     * @return the count of emails sent on that day
     */
    public int getDailyCount(LocalDate day) {
        return query("SELECT count FROM email_daily_count WHERE day = :day;")
                .single(call().bind("day", day))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * Increments the daily send count for the given day, using upsert to handle first-of-day inserts.
     *
     * @param day the date to increment
     */
    public void incrementDailyCount(LocalDate day) {
        query("""
                INSERT INTO email_daily_count(day, count) VALUES(:day, 1)
                ON CONFLICT (day) DO UPDATE SET count = email_daily_count.count + 1;""").single(call().bind("day", day)).insert();
    }

    /**
     * Removes sent and failed email entries and daily count records older than the specified retention period.
     *
     * @param keepDays the number of days to retain entries
     */
    public void cleanupOldEntries(int keepDays) {
        query(
                        "DELETE FROM email_queue WHERE status IN ('SENT', 'FAILED') AND created_at < now() - make_interval(days => :days);")
                .single(call().bind("days", keepDays))
                .delete();
        query("DELETE FROM email_daily_count WHERE day < :cutoff;")
                .single(call().bind("cutoff", LocalDate.now().minusDays(keepDays)))
                .delete();
    }

    /**
     * Represents an email fetched from the queue for sending.
     *
     * @param id        the queue entry ID
     * @param recipient the recipient email address
     * @param subject   the email subject
     * @param body      the HTML email body
     * @param stationId the associated station ID (null for system emails)
     */
    public record QueuedEmail(int id, String recipient, String subject, String body, Integer stationId) {}
}
