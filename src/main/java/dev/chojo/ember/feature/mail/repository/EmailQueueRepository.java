/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.repository;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.feature.mail.entity.EmailQueueStatus;
import dev.chojo.ember.feature.mail.entity.FailedMail;
import dev.chojo.ember.feature.mail.entity.FailedMailCause;
import dev.chojo.ember.feature.mail.entity.MailAttachment;
import dev.chojo.ember.feature.mail.entity.MailDeliveryStatus;
import dev.chojo.ember.feature.mail.entity.MailFailureReason;
import dev.chojo.ember.feature.mail.entity.StationFailedMails;
import dev.chojo.ember.feature.mail.entity.StationPoolUse;
import dev.chojo.ember.util.sql.SqlSupport;
import dev.chojo.ember.util.sql.Transactions;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;
import static dev.chojo.ember.util.sql.SqlSupport.count;

/**
 * Repository for the email queue, managing enqueuing, fetching, status updates, rate limiting,
 * and cleanup of outbound emails.
 */
@Singleton
public class EmailQueueRepository {

    /**
     * How long a mail may sit in sending, counted from when the worker last took it in hand, before
     * that worker counts as dead. Every send finishes or gives up well inside this, so anything older
     * was left behind rather than delayed. Counting from when the mail was written instead would
     * call every old mail on a retry stuck.
     */
    public static final int STUCK_MINUTES = 10;

    private static final String FAILED_MAIL_COLUMNS = """
            id, recipient, subject, created_at, delivery_detail,
            %s AS cause,
            %s AS failed_at,
            (status = 'FAILED' AND failure_reason IS NOT NULL) AS requeueable""".formatted(MailFailureSql.CAUSE, MailFailureSql.FAILED_AT);

    private static final RowMapping<FailedMail> FAILED_MAIL = row -> {
        var cause = row.getEnum("cause", FailedMailCause.class);
        return new FailedMail(
                row.getInt("id"),
                row.getString("recipient"),
                row.getString("subject"),
                row.get("created_at", INSTANT_TIMESTAMP),
                row.get("failed_at", INSTANT_TIMESTAMP),
                cause,
                cause.givenUp() ? null : row.getString("delivery_detail"),
                row.getBoolean("requeueable"));
    };

    private static final RowMapping<QueuedEmail> QUEUED_EMAIL = row -> new QueuedEmail(
            row.getInt("id"),
            row.getString("recipient"),
            row.getString("subject"),
            row.getString("body"),
            row.getObject("station_id", Integer.class),
            row.getInt("attempts"),
            row.getInt("provider_position"),
            row.get("created_at", INSTANT_TIMESTAMP));

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
    public void enqueue(String recipient, String subject, String body, @Nullable Integer stationId) {
        query(
                        "INSERT INTO email_queue(recipient, subject, body, station_id) VALUES(:recipient, :subject, :body, :station_id);")
                .single(call().bind("recipient", recipient)
                        .bind("subject", subject)
                        .bind("body", body)
                        .bind("station_id", stationId))
                .insert();
    }

    /**
     * Enqueues an email without a station association together with the files it carries, all or
     * nothing.
     *
     * @param recipient   the recipient email address
     * @param subject     the email subject
     * @param body        the HTML email body
     * @param attachments the files it carries, in the order the mail lists them
     */
    public void enqueueWithAttachments(
            String recipient, String subject, String body, List<MailAttachment> attachments) {
        Transactions.run(() -> {
            int emailId = SqlSupport.insertReturning(
                    """
                            INSERT INTO email_queue(recipient, subject, body)
                            VALUES (:recipient, :subject, :body)
                            RETURNING id;""",
                    call().bind("recipient", recipient).bind("subject", subject).bind("body", body),
                    row -> row.getInt("id"));
            for (var attachment : attachments) {
                query("""
                        INSERT INTO email_queue_attachment(email_id, file_name, content_type, content)
                        VALUES (:email_id, :file_name, :content_type, :content);""")
                        .single(call().bind("email_id", emailId)
                                .bind("file_name", attachment.fileName())
                                .bind("content_type", attachment.contentType())
                                .bind("content", attachment.content()))
                        .insert();
            }
        });
    }

    /**
     * @param emailId the queued email
     * @return the files it carries, in the order they were queued
     */
    public List<MailAttachment> attachmentsOf(int emailId) {
        return query("""
                        SELECT file_name, content_type, content
                        FROM email_queue_attachment
                        WHERE email_id = :email_id
                        ORDER BY id;""")
                .single(call().bind("email_id", emailId))
                .map(row -> new MailAttachment(
                        row.getString("file_name"), row.getString("content_type"), row.getBytes("content")))
                .all();
    }

    /**
     * Atomically fetches pending emails that are due and marks them as SENDING to prevent
     * double-processing. A mail whose next attempt lies in the future is left alone until then.
     * Rows another claim holds locked are skipped rather than waited for, so two workers never take
     * the same mail. Global emails (no station) can be excluded while the instance-wide mail
     * provider is not configured, so they stay queued untouched until an operator sets one up.
     *
     * @param limit         the maximum number of emails to fetch
     * @param includeGlobal whether emails without a station association are fetched
     * @return the list of emails now in SENDING state
     */
    public List<QueuedEmail> fetchPending(int limit, boolean includeGlobal) {
        return query("""
                UPDATE email_queue SET status = 'SENDING', claimed_at = now()
                WHERE id IN (
                    SELECT id FROM email_queue
                    WHERE status = 'PENDING'
                      AND next_attempt_at <= now()
                      AND (:include_global OR station_id IS NOT NULL)
                    ORDER BY created_at
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, recipient, subject, body, station_id, attempts, provider_position, created_at;""")
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
    public void recordDelivery(int id, MailDeliveryStatus status, @Nullable String detail, @Nullable String messageId) {
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
                        "SELECT id, recipient, subject, body, station_id, attempts, provider_position, created_at FROM email_queue WHERE id = :id;")
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
    public Optional<QueuedEmail> findLatestFor(
            String recipient, @Nullable String subject, @Nullable Integer stationId) {
        var where = WhereBuilder.create()
                .add("AND subject = :subject", "subject", subject)
                .add("AND station_id = :station_id", "station_id", stationId);
        return query("""
                SELECT id, recipient, subject, body, station_id, attempts, provider_position, created_at
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
     * Marks an email as successfully sent through a station's own provider and lets go of the files
     * it carried.
     *
     * @param id the queued email ID
     */
    public void markSent(int id) {
        markSent(id, null);
    }

    /**
     * Marks an email as successfully sent, recording which of the instance's providers carried it,
     * and lets go of the files it carried.
     *
     * @param id               the queued email ID
     * @param instancePosition where the provider sits in the instance's list, or null when a
     *                         station's own provider carried the mail
     */
    public void markSent(int id, @Nullable Integer instancePosition) {
        query("""
                WITH sent AS (
                    UPDATE email_queue
                    SET
                        status            = 'SENT',
                        sent_at           = now(),
                        instance_position = CAST(:instance_position AS INTEGER)
                    WHERE id = :id
                    RETURNING id
                )
                DELETE FROM email_queue_attachment WHERE email_id IN (SELECT id FROM sent);""")
                .single(call().bind("id", id).bind("instance_position", instancePosition))
                .update();
    }

    /**
     * When a mail last went out successfully, or empty when none ever has. This is the proof
     * the passwordless mode demands before it can be chosen: for every member with an address,
     * mail is the only way back in, and mail that worked in March is not mail that works in
     * September.
     */
    public Optional<Instant> findLastSentAt() {
        return query("SELECT MAX(sent_at) AS last_sent FROM email_queue WHERE status = 'SENT';")
                .single(call())
                .map(row -> row.get("last_sent", INSTANT_TIMESTAMP))
                .first()
                .filter(java.util.Objects::nonNull);
    }

    /**
     * How many mails one of a station's own providers has sent on the given day.
     *
     * <p>Read from what actually left rather than from a counter of its own, so a mail written
     * yesterday and sent today counts towards today, which is what a daily allowance means.
     *
     * @param day       the day to count
     * @param stationId the station whose list is meant
     * @param position  which provider of that list
     */
    public int ownProviderDailyCount(LocalDate day, int stationId, int position) {
        return count(
                """
                        SELECT
                            count(*) AS count
                        FROM
                            email_queue
                        WHERE
                            sent_at >= :day_start
                            AND sent_at < :day_end
                            AND station_id = :station_id
                            AND provider_position = :position
                            AND instance_position IS NULL;""",
                call().bind("day_start", day)
                        .bind("day_end", day.plusDays(1))
                        .bind("station_id", stationId)
                        .bind("position", position));
    }

    /**
     * How many mails one of the instance's providers has sent on the given day, the instance's own
     * and the stations' together, since its allowance is one for all of them.
     *
     * @param day              the day to count
     * @param instancePosition which provider of the instance's list
     */
    public int instanceProviderDailyCount(LocalDate day, int instancePosition) {
        return count(
                """
                        SELECT
                            count(*) AS count
                        FROM
                            email_queue
                        WHERE
                            sent_at >= :day_start
                            AND sent_at < :day_end
                            AND instance_position = :position;""",
                call().bind("day_start", day).bind("day_end", day.plusDays(1)).bind("position", instancePosition));
    }

    /**
     * How many station mails one of the instance's providers has sent on the given day, all
     * stations together. This is what the share stations may use of it is measured against.
     *
     * @param day              the day to count
     * @param instancePosition which provider of the instance's list
     */
    public int stationShareDailyCount(LocalDate day, int instancePosition) {
        return count(
                """
                        SELECT
                            count(*) AS count
                        FROM
                            email_queue
                        WHERE
                            sent_at >= :day_start
                            AND sent_at < :day_end
                            AND instance_position = :position
                            AND station_id IS NOT NULL;""",
                call().bind("day_start", day).bind("day_end", day.plusDays(1)).bind("position", instancePosition));
    }

    /**
     * How many of one station's mails the instance's providers sent on the given day.
     *
     * @param day              the day to count
     * @param stationId        the station
     * @param instancePosition one provider of the instance's list, or null for all of them, which
     *                         is what the station's own daily limit is measured against
     */
    public int stationViaInstanceDailyCount(LocalDate day, int stationId, @Nullable Integer instancePosition) {
        return count(
                """
                        SELECT
                            count(*) AS count
                        FROM
                            email_queue
                        WHERE
                            sent_at >= :day_start
                            AND sent_at < :day_end
                            AND station_id = :station_id
                            AND instance_position IS NOT NULL
                            AND (CAST(:position AS INTEGER) IS NULL OR instance_position = :position);""",
                call().bind("day_start", day)
                        .bind("day_end", day.plusDays(1))
                        .bind("station_id", stationId)
                        .bind("position", instancePosition));
    }

    /**
     * Which stations the instance's providers sent mail for on the given day, and how much, per
     * provider.
     *
     * @param day the day to count
     */
    public List<StationPoolUse> stationPoolUse(LocalDate day) {
        return query("""
                        SELECT
                            q.instance_position, s.uid, s.name, count(*) AS sent
                        FROM
                            email_queue q
                            JOIN station s ON s.id = q.station_id
                        WHERE
                            q.sent_at >= :day_start
                            AND q.sent_at < :day_end
                            AND q.instance_position IS NOT NULL
                        GROUP BY
                            q.instance_position, s.uid, s.name
                        ORDER BY
                            q.instance_position, sent DESC, s.name;""")
                .single(call().bind("day_start", day).bind("day_end", day.plusDays(1)))
                .map(row -> new StationPoolUse(
                        row.getInt("instance_position"),
                        row.get("uid", UUID_STRING),
                        row.getString("name"),
                        row.getInt("sent")))
                .all();
    }

    /**
     * Holds a mail back until the start of the given day, when it starts over at the first
     * provider of its list with a fresh set of attempts.
     *
     * <p>For a mail whose whole list has spent its allowance for today: nothing could carry it
     * before tomorrow, and every provider is worth trying again once the day has turned.
     *
     * @param id  the queued email ID
     * @param day the day it may go out on
     */
    public void waitUntil(int id, LocalDate day) {
        query("""
                UPDATE email_queue
                SET
                    status            = 'PENDING',
                    claimed_at        = NULL,
                    provider_position = 0,
                    attempts          = 0,
                    next_attempt_at   = :day
                WHERE id = :id;""").single(call().bind("id", id).bind("day", day)).update();
    }

    /**
     * Marks an email as failed to send and keeps why. The files it carries stay with it, so an
     * administrator can send it again; the usual cleanup removes them with the mail.
     *
     * @param id     the queued email ID
     * @param reason why the queue gave up on it
     */
    public void markFailed(int id, MailFailureReason reason) {
        query("UPDATE email_queue SET status = 'FAILED', failure_reason = :reason, failed_at = now() WHERE id = :id;")
                .single(call().bind("id", id).bind("reason", reason))
                .update();
    }

    /**
     * @return how many mails of the instance and every station did not arrive and nobody has taken
     *         note of yet
     */
    public int countUnacknowledgedFailures() {
        return count("SELECT count(*) FROM email_queue WHERE %s;".formatted(MailFailureSql.UNACKNOWLEDGED), call());
    }

    /**
     * @return how many of the instance's own mails did not arrive and nobody has taken note of yet
     */
    public int countUnacknowledgedInstanceFailures() {
        return count(
                "SELECT count(*) FROM email_queue WHERE station_id IS NULL AND %s;"
                        .formatted(MailFailureSql.UNACKNOWLEDGED),
                call());
    }

    /**
     * The instance's own mails that did not arrive and nobody has taken note of, the latest failure
     * first.
     *
     * @param limit  how many to return
     * @param offset how many to skip
     */
    public List<FailedMail> unacknowledgedInstanceFailures(int limit, int offset) {
        return query("""
                        SELECT
                            %s
                        FROM
                            email_queue
                        WHERE
                            station_id IS NULL
                            AND %s
                        ORDER BY
                            failed_at DESC, id DESC
                        LIMIT :limit OFFSET :offset;""", FAILED_MAIL_COLUMNS, MailFailureSql.UNACKNOWLEDGED)
                .single(call().bind("limit", limit).bind("offset", offset))
                .map(FAILED_MAIL)
                .all();
    }

    /**
     * One of the instance's own mails that did not arrive, whether or not somebody has taken note of it.
     *
     * @param id the mail
     * @return the mail, or empty when it is a station's, arrived, or does not exist
     */
    public Optional<FailedMail> findInstanceFailure(int id) {
        return query("""
                        SELECT
                            %s
                        FROM
                            email_queue
                        WHERE
                            id = :id
                            AND station_id IS NULL
                            AND %s;""", FAILED_MAIL_COLUMNS, MailFailureSql.DID_NOT_ARRIVE)
                .single(call().bind("id", id))
                .map(FAILED_MAIL)
                .first();
    }

    /**
     * The stations' mails that did not arrive and nobody has taken note of, one row per station and
     * cause. Nothing about a single mail leaves here.
     *
     * @return the stations, the one whose latest failure is newest first
     */
    public List<StationFailedMails> unacknowledgedStationFailures() {
        Map<UUID, StationGroup> stations = new LinkedHashMap<>();
        query("""
                        SELECT
                            s.uid, s.name, f.cause,
                            count(*)                             AS count,
                            min(f.failed_at)                     AS oldest,
                            max(f.failed_at)                     AS newest,
                            max(max(f.failed_at)) OVER (PARTITION BY s.id) AS station_newest
                        FROM
                            (SELECT station_id, %s AS cause, %s AS failed_at
                             FROM email_queue
                             WHERE station_id IS NOT NULL AND %s) f
                            JOIN station s ON s.id = f.station_id
                        GROUP BY
                            s.id, s.uid, s.name, f.cause
                        ORDER BY
                            station_newest DESC, s.name, count DESC, f.cause;""", MailFailureSql.CAUSE, MailFailureSql.FAILED_AT, MailFailureSql.UNACKNOWLEDGED)
                .single(call())
                .map(row -> new StationCauseRow(
                        row.get("uid", UUID_STRING),
                        row.getString("name"),
                        row.getEnum("cause", FailedMailCause.class),
                        row.getInt("count"),
                        row.get("oldest", INSTANT_TIMESTAMP),
                        row.get("newest", INSTANT_TIMESTAMP)))
                .all()
                .forEach(row -> stations.computeIfAbsent(
                                row.stationUid(), uid -> new StationGroup(row.stationUid(), row.name()))
                        .add(row));
        return stations.values().stream().map(StationGroup::toFailedMails).toList();
    }

    /**
     * Takes note of one of the instance's own mails that did not arrive. Taking note twice keeps the
     * first note.
     *
     * @param id the mail
     * @param by the administrator taking note
     * @return whether the mail is one of the instance's own that did not arrive
     */
    public boolean acknowledgeInstanceFailure(int id, int by) {
        return query("""
                        UPDATE email_queue
                        SET
                            acknowledged_at = coalesce(acknowledged_at, now()),
                            acknowledged_by = CASE WHEN acknowledged_at IS NULL THEN :by ELSE acknowledged_by END
                        WHERE
                            id = :id
                            AND station_id IS NULL
                            AND %s;""", MailFailureSql.DID_NOT_ARRIVE)
                        .single(call().bind("id", id).bind("by", by))
                        .update()
                        .rows()
                > 0;
    }

    /**
     * Takes note of every one of the instance's own mails that did not arrive, up to a moment, so a
     * failure that came in after the administrator looked stays unnoticed until they see it.
     *
     * @param upTo the latest failure the administrator saw
     * @param by   the administrator taking note
     * @return how many mails were taken note of
     */
    public int acknowledgeInstanceFailures(Instant upTo, int by) {
        return query("""
                        UPDATE email_queue
                        SET acknowledged_at = now(), acknowledged_by = :by
                        WHERE station_id IS NULL
                          AND %s
                          AND %s <= :up_to;""", MailFailureSql.UNACKNOWLEDGED, MailFailureSql.FAILED_AT)
                .single(call().bind("up_to", upTo, INSTANT_TIMESTAMP).bind("by", by))
                .update()
                .rows();
    }

    /**
     * Takes note of one station's mails that did not arrive, up to a moment, so a failure that came
     * in after the administrator looked stays unnoticed until they see it.
     *
     * @param stationId the station
     * @param upTo      the latest failure the administrator saw
     * @param by        the administrator taking note
     * @return how many mails were taken note of
     */
    public int acknowledgeStationFailures(int stationId, Instant upTo, int by) {
        return query("""
                        UPDATE email_queue
                        SET acknowledged_at = now(), acknowledged_by = :by
                        WHERE station_id = :station_id
                          AND %s
                          AND %s <= :up_to;""", MailFailureSql.UNACKNOWLEDGED, MailFailureSql.FAILED_AT)
                .single(call().bind("station_id", stationId)
                        .bind("up_to", upTo, INSTANT_TIMESTAMP)
                        .bind("by", by))
                .update()
                .rows();
    }

    /**
     * Puts one of the instance's own mails the queue gave up on back in the queue, at the first
     * provider with a fresh set of attempts, and forgets that it failed. Only a mail that failed since
     * the queue keeps its reason qualifies: before then the files it carried were dropped on failure.
     *
     * @param id the mail
     * @return whether the mail was put back
     */
    public boolean requeueInstanceFailure(int id) {
        return query("""
                        UPDATE email_queue
                        SET
                            status              = 'PENDING',
                            claimed_at          = NULL,
                            provider_position   = 0,
                            attempts            = 0,
                            next_attempt_at     = now(),
                            failure_reason      = NULL,
                            failed_at           = NULL,
                            acknowledged_at     = NULL,
                            acknowledged_by     = NULL,
                            delivery_status     = 'UNKNOWN',
                            delivery_detail     = NULL,
                            delivery_updated_at = NULL,
                            provider_message_id = NULL
                        WHERE
                            id = :id
                            AND station_id IS NULL
                            AND status = 'FAILED'
                            AND failure_reason IS NOT NULL;""").single(call().bind("id", id)).update().rows() > 0;
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
     * Puts an email back in the queue, not to be taken again before the delay has passed.
     *
     * @param id    the queued email ID
     * @param delay how long the queue holds it back
     */
    public void retryAfter(int id, Duration delay) {
        query("""
                UPDATE email_queue
                SET
                    status          = 'PENDING',
                    next_attempt_at = now() + make_interval(secs => :seconds)
                WHERE id = :id;""")
                .single(call().bind("id", id).bind("seconds", (int) delay.toSeconds()))
                .update();
    }

    /**
     * Renews the claim on an email the worker is about to send, so the time it waited behind the
     * rest of its batch is not mistaken for a worker that died holding it.
     *
     * @param id the queued email ID
     */
    public void renewClaim(int id) {
        query("UPDATE email_queue SET claimed_at = now() WHERE id = :id;")
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
     * @param createdAt        when the mail was queued, which is where its retry window starts
     */
    public record QueuedEmail(
            int id,
            String recipient,
            String subject,
            String body,
            @Nullable Integer stationId,
            int attempts,
            int providerPosition,
            Instant createdAt) {}

    /**
     * How the post stands, in one row.
     *
     * @param pending  waiting for the next round
     * @param sending  handed to the worker and not yet answered for
     * @param sent     accepted by a provider, whatever became of it afterwards
     * @param failed   given up on, every provider having refused it
     * @param stuck    in sending for longer than any send should take, which means a worker died
     *                 holding them: they are counted separately because nothing retries them
     * @param oldestPendingAt when the longest-waiting mail was written, or null when none waits
     */
    public record QueueSummary(
            int pending,
            int sending,
            int sent,
            int failed,
            int stuck,
            @Nullable Instant oldestPendingAt) {}

    /**
     * One mail as the dashboard shows it. The body is deliberately absent: this is a list of what
     * happened to the post, not a way to read other people's mail.
     */
    public record QueueEntry(
            int id,
            String recipient,
            String subject,
            Instant createdAt,
            @Nullable Instant sentAt,
            EmailQueueStatus status,
            MailDeliveryStatus deliveryStatus,
            @Nullable String deliveryDetail,
            int attempts,
            int providerPosition) {}

    private static final RowMapping<QueueEntry> QUEUE_ENTRY = row -> new QueueEntry(
            row.getInt("id"),
            row.getString("recipient"),
            row.getString("subject"),
            row.get("created_at", INSTANT_TIMESTAMP),
            row.get("sent_at", INSTANT_TIMESTAMP),
            row.getEnum("status", EmailQueueStatus.class),
            row.getEnum("delivery_status", MailDeliveryStatus.class),
            row.getString("delivery_detail"),
            row.getInt("attempts"),
            row.getInt("provider_position"));

    /**
     * The state of the queue for one owner.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     */
    public QueueSummary summary(@Nullable Integer stationId) {
        var where = WhereBuilder.create().add("AND station_id = :station_id", "station_id", stationId);
        String scope = stationId == null ? "AND station_id IS NULL" : where.fragment();
        return query("""
                        SELECT
                            count(*) FILTER (WHERE status = 'PENDING')                  AS pending,
                            count(*) FILTER (WHERE status = 'SENDING')                  AS sending,
                            count(*) FILTER (WHERE status = 'SENT')                     AS sent,
                            count(*) FILTER (WHERE status = 'FAILED')                   AS failed,
                            count(*) FILTER (WHERE status = 'SENDING'
                                             AND claimed_at < now() - make_interval(mins => %d)) AS stuck,
                            min(created_at) FILTER (WHERE status = 'PENDING')           AS oldest
                        FROM
                            email_queue
                        WHERE
                            1 = 1 %s;""", STUCK_MINUTES, scope)
                .single(stationId == null ? call() : where.apply(call()))
                .map(row -> new QueueSummary(
                        row.getInt("pending"),
                        row.getInt("sending"),
                        row.getInt("sent"),
                        row.getInt("failed"),
                        row.getInt("stuck"),
                        row.get("oldest", INSTANT_TIMESTAMP)))
                .first()
                .orElse(new QueueSummary(0, 0, 0, 0, 0, null));
    }

    /**
     * How many waiting mails sit on each provider of the list.
     *
     * <p>This is what says who is next: a mail waits at the provider whose turn it is, and moves on
     * only once that one has used its attempts or its allowance.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @return position to number of mails waiting there
     */
    public Map<Integer, Integer> pendingByProvider(@Nullable Integer stationId) {
        var where = WhereBuilder.create().add("AND station_id = :station_id", "station_id", stationId);
        String scope = stationId == null ? "AND station_id IS NULL" : where.fragment();
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        query("""
                        SELECT
                            provider_position, count(*) AS waiting
                        FROM
                            email_queue
                        WHERE
                            status IN ('PENDING', 'SENDING') %s
                        GROUP BY
                            provider_position
                        ORDER BY
                            provider_position;""", scope)
                .single(stationId == null ? call() : where.apply(call()))
                .map(row -> Map.entry(row.getInt("provider_position"), row.getInt("waiting")))
                .all()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /**
     * The mails a dead worker left in sending, oldest first.
     *
     * <p>Nothing picks these up again on its own, which is the whole reason they can be named: the
     * counter alone says how many there are without saying which, and an operator cannot act on a
     * number.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @param limit     how many to return
     */
    public List<QueueEntry> stuck(@Nullable Integer stationId, int limit) {
        var where = WhereBuilder.create().add("AND station_id = :station_id", "station_id", stationId);
        String scope = stationId == null ? "AND station_id IS NULL" : where.fragment();
        return query("""
                        SELECT
                            id, recipient, subject, created_at, sent_at, status,
                            delivery_status, delivery_detail, attempts, provider_position
                        FROM
                            email_queue
                        WHERE
                            status = 'SENDING'
                            AND claimed_at < now() - make_interval(mins => %d)
                            %s
                        ORDER BY
                            claimed_at
                        LIMIT :limit;""", STUCK_MINUTES, scope)
                .single((stationId == null ? call() : where.apply(call())).bind("limit", limit))
                .map(QUEUE_ENTRY)
                .all();
    }

    /**
     * Puts left-behind mails back in the queue so the next round carries them.
     *
     * <p>Scoped to the owner and to the stuck ones on purpose: a mail the worker is holding right
     * now must not be pulled out from under it, and one owner must not reach into another's post.
     * A mail put back is due at once, whatever backoff it was under, and keeps the attempts it has
     * already used: the send it was left behind in was never counted.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @param id        the one mail meant, or null for every stuck one
     * @return how many mails were put back
     */
    public int requeueStuck(@Nullable Integer stationId, @Nullable Integer id) {
        var where = WhereBuilder.create()
                .add("AND station_id = :station_id", "station_id", stationId)
                .add("AND id = :id", "id", id);
        String owner = stationId == null ? "AND station_id IS NULL" : "";
        return query("""
                        UPDATE email_queue
                        SET
                            status          = 'PENDING',
                            claimed_at      = NULL,
                            next_attempt_at = now()
                        WHERE
                            status = 'SENDING'
                            AND claimed_at < now() - make_interval(mins => %d)
                            %s
                            %s;""", STUCK_MINUTES, owner, where.fragment())
                .single(where.apply(call()))
                .update()
                .rows();
    }

    /**
     * The most recent mails of one owner, newest first.
     *
     * @param stationId the station whose post is meant, or null for the instance's
     * @param limit     how many to return
     */
    public List<QueueEntry> recent(@Nullable Integer stationId, int limit) {
        var where = WhereBuilder.create().add("AND station_id = :station_id", "station_id", stationId);
        String scope = stationId == null ? "AND station_id IS NULL" : where.fragment();
        return query("""
                        SELECT
                            id, recipient, subject, created_at, sent_at, status,
                            delivery_status, delivery_detail, attempts, provider_position
                        FROM
                            email_queue
                        WHERE
                            1 = 1 %s
                        ORDER BY
                            created_at DESC
                        LIMIT :limit;""", scope)
                .single((stationId == null ? call() : where.apply(call())).bind("limit", limit))
                .map(QUEUE_ENTRY)
                .all();
    }

    private record StationCauseRow(
            UUID stationUid, String name, FailedMailCause cause, int count, Instant oldest, Instant newest) {}

    /** One station's failed mails, gathered from its rows per cause. */
    private static final class StationGroup {
        private final UUID stationUid;
        private final String name;
        private final List<StationFailedMails.CauseCount> causes = new ArrayList<>();
        private int count;
        private @Nullable Instant oldest;
        private @Nullable Instant newest;

        StationGroup(UUID stationUid, String name) {
            this.stationUid = stationUid;
            this.name = name;
        }

        StationGroup add(StationCauseRow row) {
            causes.add(new StationFailedMails.CauseCount(row.cause(), row.count()));
            count += row.count();
            Instant first = oldest;
            Instant last = newest;
            oldest = first == null || row.oldest().isBefore(first) ? row.oldest() : first;
            newest = last == null || row.newest().isAfter(last) ? row.newest() : last;
            return this;
        }

        StationFailedMails toFailedMails() {
            return new StationFailedMails(
                    stationUid,
                    name,
                    count,
                    Objects.requireNonNull(oldest, "a group holds at least one row"),
                    Objects.requireNonNull(newest, "a group holds at least one row"),
                    List.copyOf(causes));
        }
    }
}
