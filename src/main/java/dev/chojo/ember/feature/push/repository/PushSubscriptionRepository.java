/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.push.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.push.entity.PushSubscription;
import jakarta.inject.Singleton;

import java.time.Duration;
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
     * registered last, with that sign-in and fresh keys, and its count of refusals starts over. It
     * counts as registered now, so the cap on devices keeps a browser in use over one left idle. What
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
                                created_at    = now(),
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
                        INSERT INTO push_browser (account_id, endpoint, allowed)
                        SELECT account_id, endpoint, FALSE FROM gone
                        ON CONFLICT (account_id, endpoint) DO UPDATE SET allowed = FALSE, changed_at = now();""")
                .single(call().bind("id", id).bind("account_id", accountId))
                .insert()
                .changed();
    }

    /**
     * Remembers that the account allowed push on a browser, or registered it again, which lets the page register
     * it again on its own after the sign-in it was registered in ended.
     */
    public void allow(int accountId, String endpoint) {
        query("""
                        INSERT INTO push_browser (account_id, endpoint, allowed)
                        VALUES (:account_id, :endpoint, TRUE)
                        ON CONFLICT (account_id, endpoint) DO UPDATE SET allowed = TRUE, changed_at = now();""")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .insert();
    }

    /**
     * Whether the account allowed push on a browser or removed it by hand, as it last did.
     *
     * @return true where allowed, false where removed, empty where the account never did either
     */
    public Optional<Boolean> allowed(int accountId, String endpoint) {
        return query("SELECT allowed FROM push_browser WHERE account_id = :account_id AND endpoint = :endpoint;")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .map(row -> row.getBoolean("allowed"))
                .first();
    }

    /** @return whether the account removed any browser from push by hand, which is a choice made about push */
    public boolean declinedAny(int accountId) {
        return query("SELECT 1 FROM push_browser WHERE account_id = :account_id AND NOT allowed LIMIT 1;")
                .single(call().bind("account_id", accountId))
                .map(row -> true)
                .first()
                .orElse(false);
    }

    /**
     * Forgets an address a browser no longer uses, once it moved to a new one: what the account allowed there and
     * the registration still bound to it.
     */
    public void forgetAddress(int accountId, String endpoint) {
        query("DELETE FROM push_browser WHERE account_id = :account_id AND endpoint = :endpoint;")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .delete();
        query("DELETE FROM push_subscription WHERE account_id = :account_id AND endpoint = :endpoint;")
                .single(call().bind("account_id", accountId).bind("endpoint", endpoint))
                .delete();
    }

    /** Forgets a browser whose registration cannot be used any more; what its account allowed there stays. */
    public void delete(int id) {
        query("DELETE FROM push_subscription WHERE id = :id;")
                .single(call().bind("id", id))
                .delete();
    }

    /**
     * Forgets a browser the push service no longer knows, and what any account allowed or removed there, since the
     * address will not come back.
     */
    public void deleteGone(int id) {
        query("""
                        WITH gone AS (DELETE FROM push_subscription WHERE id = :id RETURNING endpoint)
                        DELETE FROM push_browser b USING gone WHERE b.endpoint = gone.endpoint;""").single(call().bind("id", id)).delete();
    }

    /**
     * Forgets what accounts allowed or removed on browsers that have not been registered for a while, so a push
     * address is not kept for good once its browser stopped coming back.
     *
     * @param unused how long a browser without a registration is remembered after its last change
     */
    public void pruneBrowsers(Duration unused) {
        query("""
                        DELETE FROM push_browser b
                        WHERE b.changed_at < now() - CAST(:unused AS INTERVAL)
                          AND NOT EXISTS (
                              SELECT 1 FROM push_subscription s
                              WHERE s.account_id = b.account_id AND s.endpoint = b.endpoint);""")
                .single(call().bind("unused", unused.toSeconds() + " seconds"))
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
