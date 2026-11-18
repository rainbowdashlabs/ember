/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/** The browsers accounts allowed push messages on. */
@Singleton
public class PushSubscriptionRepository {
    private static final String COLUMNS = PushSubscription.columns("s");

    /**
     * Registers a browser, or refreshes it when its address is known already.
     *
     * <p>The address belongs to one browser. Registered again, under the same account or another
     * one (a shared computer the last person did not sign out of), the row moves to whoever
     * registered last, with that sign-in and fresh keys, and its count of refusals starts over. What
     * was still waiting for the previous account is dropped first, so it never reaches the new one.
     *
     * @return the subscription as stored
     */
    public PushSubscription register(
            int accountId, int sessionId, String endpoint, byte[] p256dh, byte[] auth, String label) {
        query("""
                        DELETE FROM push_outbox o
                        USING push_subscription s
                        WHERE o.subscription_id = s.id
                          AND s.endpoint = :endpoint
                          AND s.account_id <> :account_id
                          AND o.sent_at IS NULL;""")
                .single(call().bind("endpoint", endpoint).bind("account_id", accountId))
                .delete();
        return query("""
                        INSERT INTO push_subscription AS s (account_id, session_id, endpoint, p256dh, auth, label)
                        VALUES (:account_id, :session_id, :endpoint, :public_key, :auth, :label)
                        ON CONFLICT (endpoint) DO UPDATE
                            SET account_id    = excluded.account_id,
                                session_id    = excluded.session_id,
                                p256dh        = excluded.p256dh,
                                auth          = excluded.auth,
                                label         = excluded.label,
                                failure_count = 0
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("account_id", accountId)
                        .bind("session_id", sessionId)
                        .bind("endpoint", endpoint)
                        .bind("public_key", p256dh)
                        .bind("auth", auth)
                        .bind("label", label))
                .map(PushSubscription.map())
                .first()
                .orElseThrow();
    }

    /**
     * Forgets the account's browsers beyond the newest {@code keep}, those registered longest ago
     * first.
     */
    public void keepNewest(int accountId, int keep) {
        query("""
                        DELETE FROM push_subscription
                        WHERE account_id = :account_id
                          AND id NOT IN (
                              SELECT id FROM push_subscription
                              WHERE account_id = :account_id
                              ORDER BY created_at DESC, id DESC
                              LIMIT :keep);""")
                .single(call().bind("account_id", accountId).bind("keep", keep))
                .delete();
    }

    /** @return the account's browsers, newest first */
    public List<PushSubscription> findByAccount(int accountId) {
        return query(
                        "SELECT %s FROM push_subscription s WHERE s.account_id = :account_id ORDER BY s.created_at DESC, s.id DESC;",
                        COLUMNS)
                .single(call().bind("account_id", accountId))
                .map(PushSubscription.map())
                .all();
    }

    /** @return the subscription, empty when it is not the account's */
    public Optional<PushSubscription> findOwn(int id, int accountId) {
        return query("SELECT %s FROM push_subscription s WHERE s.id = :id AND s.account_id = :account_id;", COLUMNS)
                .single(call().bind("id", id).bind("account_id", accountId))
                .map(PushSubscription.map())
                .first();
    }

    /** @return the subscriptions with these ids that still exist */
    public List<PushSubscription> findByIds(List<Integer> ids) {
        if (ids.isEmpty()) return List.of();
        return query("SELECT %s FROM push_subscription s WHERE s.id = ANY(:ids);", COLUMNS)
                .single(call().bind("ids", ids, PostgreSqlTypes.INTEGER))
                .map(PushSubscription.map())
                .all();
    }

    /**
     * Forgets one of the account's browsers and remembers that it was removed by hand, so it is not registered
     * again without being asked.
     *
     * @return true when it was the account's and is gone
     */
    public boolean declineOwn(int id, int accountId) {
        return query("""
                        WITH gone AS (
                            DELETE FROM push_subscription
                            WHERE id = :id AND account_id = :account_id
                            RETURNING account_id, endpoint)
                        INSERT INTO push_declined (account_id, endpoint)
                        SELECT account_id, endpoint FROM gone
                        ON CONFLICT (account_id, endpoint) DO UPDATE SET declined_at = now();""")
                .single(call().bind("id", id).bind("account_id", accountId))
                .insert()
                .changed();
    }

    /** @return whether the account removed this browser by hand and has not allowed it again since */
    public boolean declined(int accountId, String endpoint) {
        return query("SELECT 1 FROM push_declined WHERE account_id = :account_id AND endpoint = :endpoint;")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    /** Lets a browser the account removed by hand be registered again, because the account allowed it anew. */
    public void allowAgain(int accountId, String endpoint) {
        query("DELETE FROM push_declined WHERE account_id = :account_id AND endpoint = :endpoint;")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .delete();
    }

    /** Forgets a browser the push service no longer knows. */
    public void delete(int id) {
        query("DELETE FROM push_subscription WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /** Notes that a push reached the push service, which ends a run of refusals. */
    public void recordSuccess(int id) {
        query("UPDATE push_subscription SET last_success_at = now(), failure_count = 0 WHERE id = :id;")
                .single(call().bind("id", id))
                .update();
    }

    /**
     * Notes a refused push.
     *
     * @return the refusals in a row now, 0 when the subscription is gone already
     */
    public int recordFailure(int id) {
        return query(
                        "UPDATE push_subscription SET failure_count = failure_count + 1 WHERE id = :id RETURNING failure_count;")
                .single(call().bind("id", id))
                .map(row -> row.getInt("failure_count"))
                .first()
                .orElse(0);
    }
}
