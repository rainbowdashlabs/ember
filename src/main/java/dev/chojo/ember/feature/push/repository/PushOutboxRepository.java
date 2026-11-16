/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The pushes waiting to go out, and those sent within the hour the rate limit counts.
 *
 * <p>A row is claimed by moving its due time a lease ahead in the same statement that picks it, with
 * the rows another node is working on skipped, so two nodes never send the same push. A sender that
 * dies mid-way leaves its rows to be picked up again when the lease runs out.
 */
@Singleton
public class PushOutboxRepository {

    /**
     * A push that is due.
     *
     * @param id             the row
     * @param subscriptionId the browser it goes to
     * @param kind           {@code TEST} or {@code NOTIFICATION}
     * @param notificationId the notification it is about, for {@code NOTIFICATION}
     * @param attempts       how often sending was tried before
     * @param deferred       whether the rate limit held it back
     */
    public record Due(
            long id,
            int subscriptionId,
            String kind,
            @Nullable Integer notificationId,
            int attempts,
            boolean deferred) {}

    /** Queues a test push to one browser. */
    public void enqueueTest(int subscriptionId) {
        query("INSERT INTO push_outbox (subscription_id, kind) VALUES (:subscription_id, 'TEST');")
                .single(call().bind("subscription_id", subscriptionId))
                .insert();
    }

    /**
     * Claims the pushes that are due, for browsers whose sign-in is still valid.
     *
     * @param limit how many to claim at most
     * @param lease how long a claimed push is left alone before another sweep may take it again
     * @return the claimed pushes, oldest due first
     */
    public List<Due> claimDue(int limit, Duration lease) {
        return query("""
                        UPDATE push_outbox o
                        SET next_attempt_at = now() + make_interval(secs => :lease)
                        WHERE o.id IN (
                            SELECT due.id
                            FROM push_outbox due
                            JOIN push_subscription s ON s.id = due.subscription_id
                            JOIN account_session x ON x.id = s.session_id
                            WHERE due.sent_at IS NULL
                              AND due.next_attempt_at <= now()
                              AND x.expires_at > now()
                            ORDER BY due.next_attempt_at
                            LIMIT :limit
                            FOR UPDATE OF due SKIP LOCKED)
                        RETURNING o.id, o.subscription_id, o.kind, o.notification_id, o.attempts, o.deferred;""")
                .single(call().bind("limit", limit).bind("lease", (double) lease.toSeconds()))
                .map(row -> new Due(
                        row.getLong("id"),
                        row.getInt("subscription_id"),
                        row.getString("kind"),
                        row.getObject("notification_id", Integer.class),
                        row.getInt("attempts"),
                        row.getBoolean("deferred")))
                .all();
    }

    /** @return how many pushes reached the browser's push service within the last hour */
    public int sentWithinHour(int subscriptionId) {
        return query("""
                        SELECT count(*) AS sent FROM push_outbox
                        WHERE subscription_id = :subscription_id AND sent_at > now() - INTERVAL '1 hour';""")
                .single(call().bind("subscription_id", subscriptionId))
                .map(row -> row.getInt("sent"))
                .first()
                .orElse(0);
    }

    /**
     * When the browser may take a push again under the hourly limit: an hour after the send that has
     * to leave the window for the count to drop below the limit.
     *
     * @param perHour the limit
     * @return the moment, empty when the browser is below the limit now
     */
    public Optional<Instant> sendableAgainAt(int subscriptionId, int perHour) {
        return query("""
                        WITH recent AS (
                            SELECT sent_at FROM push_outbox
                            WHERE subscription_id = :subscription_id AND sent_at > now() - INTERVAL '1 hour'),
                        total AS (SELECT count(*) AS sent FROM recent)
                        SELECT recent.sent_at + INTERVAL '1 hour' AS opens
                        FROM recent, total
                        WHERE total.sent >= :per_hour
                        ORDER BY recent.sent_at
                        OFFSET (SELECT greatest(sent - :per_hour, 0) FROM total)
                        LIMIT 1;""")
                .single(call().bind("subscription_id", subscriptionId).bind("per_hour", perHour))
                .map(row -> row.get("opens", INSTANT_TIMESTAMP))
                .first();
    }

    /** Holds a push back for the rate limit until the given moment. */
    public void defer(long id, Instant until) {
        query("UPDATE push_outbox SET next_attempt_at = :until, deferred = TRUE WHERE id = :id;")
                .single(call().bind("id", id).bind("until", until, INSTANT_TIMESTAMP))
                .update();
    }

    /**
     * Claims a browser's held-back pushes that are due and that nobody is working on, so they can
     * go out together with one this sweep already holds.
     *
     * @param lease how long they are left alone
     * @return the claimed rows
     */
    public List<Long> claimHeldBack(int subscriptionId, Duration lease) {
        return query("""
                        UPDATE push_outbox o
                        SET next_attempt_at = now() + make_interval(secs => :lease)
                        WHERE o.id IN (
                            SELECT held.id FROM push_outbox held
                            WHERE held.subscription_id = :subscription_id
                              AND held.deferred AND held.sent_at IS NULL AND held.next_attempt_at <= now()
                            FOR UPDATE SKIP LOCKED)
                        RETURNING o.id;""")
                .single(call().bind("subscription_id", subscriptionId).bind("lease", (double) lease.toSeconds()))
                .map(row -> row.getLong("id"))
                .all();
    }

    /**
     * Marks one push as taken and drops the others a summary stood in for, so the summary counts
     * once against the hourly limit.
     */
    public void markSummarySent(long sentId, List<Long> absorbed) {
        markSent(List.of(sentId));
        var rest = absorbed.stream().filter(id -> id != sentId).toList();
        if (rest.isEmpty()) return;
        query("DELETE FROM push_outbox WHERE id = ANY(:ids);")
                .single(call().bind("ids", rest, PostgreSqlTypes.BIGINT))
                .delete();
    }

    /** Marks pushes as taken by the push service. */
    public void markSent(List<Long> ids) {
        if (ids.isEmpty()) return;
        query("UPDATE push_outbox SET sent_at = now() WHERE id = ANY(:ids);")
                .single(call().bind("ids", ids, PostgreSqlTypes.BIGINT))
                .update();
    }

    /** Leaves a push for another try at the given moment. */
    public void retryLater(long id, Instant at) {
        query("UPDATE push_outbox SET attempts = attempts + 1, next_attempt_at = :at WHERE id = :id;")
                .single(call().bind("id", id).bind("at", at, INSTANT_TIMESTAMP))
                .update();
    }

    /** Gives up on a push. */
    public void drop(long id) {
        query("DELETE FROM push_outbox WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /**
     * Drops every push still waiting, for when the instance switched push off: switched on again
     * later, it starts from nothing rather than delivering what piled up meanwhile.
     */
    public void dropWaiting() {
        query("DELETE FROM push_outbox WHERE sent_at IS NULL;").single(call()).delete();
    }

    /**
     * Forgets what no longer matters: pushes sent more than an hour ago, which the rate limit no
     * longer counts, and pushes nobody could deliver for a day, which would only arrive stale.
     */
    public void prune() {
        query("""
                        DELETE FROM push_outbox
                        WHERE sent_at < now() - INTERVAL '1 hour'
                           OR (sent_at IS NULL AND created_at < now() - INTERVAL '1 day');""").single(call()).delete();
    }
}
