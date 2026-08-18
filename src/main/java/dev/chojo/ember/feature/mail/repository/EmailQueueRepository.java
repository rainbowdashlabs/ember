/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * Repository for the email queue, managing enqueuing, fetching, status updates, rate limiting,
 * and cleanup of outbound emails.
 */
@Singleton
public class EmailQueueRepository {

    private static final RowMapping<QueuedEmail> QUEUED_EMAIL = row -> new QueuedEmail(
            row.getInt("id"),
            row.getString("recipient"),
            row.getString("subject"),
            row.getString("body"),
            row.getObject("station_id", Integer.class),
            row.getInt("attempts"),
            row.getInt("provider_position"));

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
                RETURNING id, recipient, subject, body, station_id, attempts, provider_position;""")
                .single(call().bind("limit", limit).bind("include_global", includeGlobal))
                .map(QUEUED_EMAIL)
                .all();
    }

    /**
     * Records what a provider reported about an email after it had accepted it.
     *
     * @param id        the queued email ID
     * @param status    the delivery outcome
     * @param detail    the reason the provider gave, or null
     * @param messageId the message id the provider assigned, or null when it named none
     */
    public void recordDelivery(int id, MailDeliveryStatus status, String detail, String messageId) {
        query("""
                UPDATE email_queue
                SET
                    delivery_status     = :status,
                    delivery_detail     = :detail,
                    delivery_updated_at = now(),
                    provider_message_id = coalesce(:message_id, provider_message_id)
                WHERE id = :id;""")
                .single(call().bind("id", id)
                        .bind("status", status.name())
                        .bind("detail", detail)
                        .bind("message_id", messageId))
                .update();
    }

    /**
     * Finds the email a provider event belongs to when the event carried our own token back.
     *
     * @param id the queued email ID taken from the event
     * @return the email, or empty when it has since been cleaned up
     */
    public Optional<QueuedEmail> findById(int id) {
        return query(
                        "SELECT id, recipient, subject, body, station_id, attempts, provider_position FROM email_queue WHERE id = :id;")
                .single(call().bind("id", id))
                .map(QUEUED_EMAIL)
                .first();
    }

    /**
     * Finds the email a provider event belongs to when the event carried no token of ours.
     *
     * <p>Falls back to what every event does name: the recipient and the subject. The most recent
     * match wins, because a repeated subject to the same address is almost always the same mail
     * being sent again.
     *
     * @param recipient the address the event names
     * @param subject   the subject the event names, or null when it named none
     * @param stationId the station the report is limited to, or null for no limit
     * @return the most recently queued match
     */
    public Optional<QueuedEmail> findLatestFor(String recipient, String subject, Integer stationId) {
        var where = WhereBuilder.create()
                .add("AND subject = :subject", "subject", subject)
                .add("AND station_id = :station_id", "station_id", stationId);
        return query("""
                SELECT id, recipient, subject, body, station_id, attempts, provider_position
                FROM email_queue
                WHERE recipient = :recipient
                  %s
                ORDER BY created_at DESC
                LIMIT 1;""", where.fragment())
                .single(where.apply(call().bind("recipient", recipient)))
                .map(QUEUED_EMAIL)
                .first();
    }

    /**
     * Records one used-up attempt against the provider currently in turn.
     *
     * @param id the queued email ID
     */
    public void countAttempt(int id) {
        query("UPDATE email_queue SET attempts = attempts + 1 WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Hands the mail to the next provider in the chain and starts its attempts over.
     *
     * @param id the queued email ID
     */
    public void advanceProvider(int id) {
        query("UPDATE email_queue SET provider_position = provider_position + 1, attempts = 0 WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Marks an email as successfully sent.
     *
     * @param id the queued email ID
     */
    public void markSent(int id) {
        query("UPDATE email_queue SET status = 'SENT', sent_at = now() WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * How many mails one provider of a chain has sent on the given day.
     *
     * <p>Read from what actually left rather than from a counter of its own, so a mail written
     * yesterday and sent today counts towards today, which is what a daily allowance means.
     *
     * @param day       the day to count
     * @param stationId the station whose chain is meant, or null for the instance chain
     * @param position  which provider of that chain
     */
    public int getProviderDailyCount(LocalDate day, Integer stationId, int position) {
        return count(
                """
                        SELECT
                            count(*) AS count
                        FROM
                            email_queue
                        WHERE
                            sent_at >= :day_start
                            AND sent_at < :day_end
                            AND provider_position = :position
                            AND station_id IS NOT DISTINCT FROM CAST(:station_id AS INTEGER);""",
                call().bind("day_start", day)
                        .bind("day_end", day.plusDays(1))
                        .bind("position", position)
                        .bind("station_id", stationId));
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
        return count("SELECT count(*) FROM email_queue WHERE status = 'PENDING';", call());
    }

    /**
     * Gets the number of global emails sent on a given day.
     *
     * @param day the date to query
     * @return the count of emails sent on that day
     */
    public int getDailyCount(LocalDate day) {
        return count("SELECT count FROM email_daily_count WHERE day = :day;", call().bind("day", day));
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
    /**
     * @param attempts         how many times the provider currently in turn has tried this mail
     * @param providerPosition which provider of the chain is in turn, counted from zero
     */
    public record QueuedEmail(
            int id,
            String recipient,
            String subject,
            String body,
            Integer stationId,
            int attempts,
            int providerPosition) {}
}
