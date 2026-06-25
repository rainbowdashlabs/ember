/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import dev.chojo.ember.feature.discovery.entity.DiscoveryPing;
import dev.chojo.ember.feature.discovery.entity.PingDirection;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class DiscoveryPingRepository {

    /**
     * Records a nonce. Returns {@code false} if the nonce was already present (replay or
     * loop).
     */
    public boolean record(String nonce, PingDirection direction, String peerKey, Instant issuedAt, Instant expiresAt) {
        int rows = query("""
                INSERT INTO discovery_ping (nonce, direction, peer_key, issued_at, expires_at)
                VALUES (:nonce, :direction, :peer_key, :issued_at, :expires_at)
                ON CONFLICT (nonce) DO NOTHING;""")
                .single(call().bind("nonce", nonce)
                        .bind("direction", direction.name())
                        .bind("peer_key", peerKey)
                        .bind("issued_at", issuedAt, INSTANT_TIMESTAMP)
                        .bind("expires_at", expiresAt, INSTANT_TIMESTAMP))
                .insert()
                .rows();
        return rows > 0;
    }

    public Optional<DiscoveryPing> findByNonce(String nonce) {
        return query("SELECT * FROM discovery_ping WHERE nonce = :nonce;")
                .single(call().bind("nonce", nonce))
                .map(DiscoveryPing.map())
                .first();
    }

    /**
     * Drops expired nonces. Called periodically by {@code DiscoveryNonceGc}.
     */
    public int deleteExpired() {
        return query("DELETE FROM discovery_ping WHERE expires_at < now();")
                .single(call())
                .delete()
                .rows();
    }
}
