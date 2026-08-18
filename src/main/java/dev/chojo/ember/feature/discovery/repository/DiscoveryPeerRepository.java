/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.PeerSource;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.insertReturning;

@Singleton
public class DiscoveryPeerRepository {
    private static final String DISCOVERY_PEER_COLUMNS = """
            public_key, base_url, instance_id, first_seen_at, last_seen_at, last_pinged_at, \
            last_reached_at, reachable, source, introduced_by, reputation, blocked""";

    /**
     * Returns every peer regardless of reachability. Used by the admin UI.
     */
    public List<DiscoveryPeer> findAll() {
        return query("SELECT %s FROM discovery_peer ORDER BY last_seen_at DESC;", DISCOVERY_PEER_COLUMNS)
                .single(call())
                .map(DiscoveryPeer.map())
                .all();
    }

    /**
     * Returns peers that are still candidates for outbound traffic - not blocked, not in
     * deep back-off due to negative reputation.
     */
    public List<DiscoveryPeer> findUsable() {
        return query(
                        "SELECT %s FROM discovery_peer WHERE blocked = FALSE AND reputation > -50 ORDER BY last_seen_at DESC;",
                        DISCOVERY_PEER_COLUMNS)
                .single(call())
                .map(DiscoveryPeer.map())
                .all();
    }

    /**
     * Returns peers we should currently consider reachable and which therefore feed the
     * discovery page.
     */
    public List<DiscoveryPeer> findReachable() {
        return query(
                        "SELECT %s FROM discovery_peer WHERE reachable = TRUE AND blocked = FALSE ORDER BY last_seen_at DESC;",
                        DISCOVERY_PEER_COLUMNS)
                .single(call())
                .map(DiscoveryPeer.map())
                .all();
    }

    public Optional<DiscoveryPeer> findByPublicKey(String publicKey) {
        return query("SELECT %s FROM discovery_peer WHERE public_key = :public_key;", DISCOVERY_PEER_COLUMNS)
                .single(call().bind("public_key", publicKey))
                .map(DiscoveryPeer.map())
                .first();
    }

    public Optional<DiscoveryPeer> findByBaseUrl(String baseUrl) {
        return query("SELECT %s FROM discovery_peer WHERE base_url = :base_url;", DISCOVERY_PEER_COLUMNS)
                .single(call().bind("base_url", baseUrl))
                .map(DiscoveryPeer.map())
                .first();
    }

    /**
     * Inserts a peer if absent, otherwise refreshes its observed {@code baseUrl} and
     * {@code lastSeenAt}. Returns the resulting row.
     */
    public DiscoveryPeer upsert(
            String publicKey, String baseUrl, String instanceId, PeerSource source, String introducedBy) {
        return insertReturning(
                """
                INSERT INTO discovery_peer (public_key, base_url, instance_id, source, introduced_by)
                VALUES (:public_key, :base_url, :instance_id, :source, :introduced_by)
                ON CONFLICT (public_key) DO UPDATE
                SET base_url     = excluded.base_url,
                    instance_id  = excluded.instance_id,
                    last_seen_at = now()
                RETURNING %s;""",
                call().bind("public_key", publicKey)
                        .bind("base_url", baseUrl)
                        .bind("instance_id", instanceId)
                        .bind("source", source.name())
                        .bind("introduced_by", introducedBy),
                DiscoveryPeer.map(),
                DISCOVERY_PEER_COLUMNS);
    }

    public void markPinged(String publicKey, Instant when) {
        query("UPDATE discovery_peer SET last_pinged_at = :when WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey).bind("when", when, INSTANT_TIMESTAMP))
                .update();
    }

    public void markReached(String publicKey, Instant when) {
        query(
                        "UPDATE discovery_peer SET last_reached_at = :when, last_seen_at = :when, reachable = TRUE WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey).bind("when", when, INSTANT_TIMESTAMP))
                .update();
    }

    public void markUnreachable(String publicKey) {
        query("UPDATE discovery_peer SET reachable = FALSE WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey))
                .update();
    }

    public void addReputation(String publicKey, int delta) {
        query("UPDATE discovery_peer SET reputation = reputation + :delta WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey).bind("delta", delta))
                .update();
    }

    public void setBlocked(String publicKey, boolean blocked) {
        query("UPDATE discovery_peer SET blocked = :blocked WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey).bind("blocked", blocked))
                .update();
    }

    public boolean delete(String publicKey) {
        return query("DELETE FROM discovery_peer WHERE public_key = :public_key;")
                .single(call().bind("public_key", publicKey))
                .delete()
                .changed();
    }

    /**
     * Reputation decay: pull all negative reputations toward zero by {@code step} every
     * decay cycle.
     */
    public int decayReputation(int step) {
        return query("""
                UPDATE discovery_peer
                SET reputation = least(reputation + :step, 0)
                WHERE reputation < 0;""").single(call().bind("step", step)).update().rows();
    }

    public List<DiscoveryPeer> findManyByKeys(List<String> publicKeys) {
        if (publicKeys.isEmpty()) return List.of();
        return query("SELECT %s FROM discovery_peer WHERE public_key = ANY(:keys);", DISCOVERY_PEER_COLUMNS)
                .single(call().bind("keys", publicKeys, PostgreSqlTypes.VARCHAR))
                .map(DiscoveryPeer.map())
                .all();
    }
}
