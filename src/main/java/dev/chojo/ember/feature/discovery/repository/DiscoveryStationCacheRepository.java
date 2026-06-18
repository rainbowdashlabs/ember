/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

@Singleton
public class DiscoveryStationCacheRepository {

    /**
     * Inserts or refreshes a cached station card for a peer.
     */
    public void upsert(String instancePublicKey, DiscoveryStationCard card, Instant fetchedAt) {
        query("""
                                INSERT INTO discovery_station_cache (instance_public_key, station_uid, payload, fetched_at)
                                VALUES (:instance_public_key, :station_uid, :payload::jsonb, :fetched_at)
                                ON CONFLICT (instance_public_key, station_uid) DO UPDATE
                                SET payload    = EXCLUDED.payload,
                                    fetched_at = EXCLUDED.fetched_at;""")
                .single(call().bind("instance_public_key", instancePublicKey)
                        .bind("station_uid", card.stationUid())
                        .bind("payload", card.toJsonString())
                        .bind("fetched_at", fetchedAt, INSTANT_TIMESTAMP))
                .insert();
    }

    /**
     * Removes cached entries for a peer that are no longer present in its latest listing
     * response. Idempotent.
     */
    public int deleteMissing(String instancePublicKey, List<String> presentStationUids) {
        if (presentStationUids.isEmpty()) {
            return query("DELETE FROM discovery_station_cache WHERE instance_public_key = :instance_public_key;")
                    .single(call().bind("instance_public_key", instancePublicKey))
                    .delete()
                    .rows();
        }
        return query("""
                                DELETE FROM discovery_station_cache
                                WHERE instance_public_key = :instance_public_key
                                  AND station_uid <> ALL(:present);""")
                .single(call().bind("instance_public_key", instancePublicKey)
                        .bind("present", presentStationUids, PostgreSqlTypes.VARCHAR))
                .delete()
                .rows();
    }

    public List<CachedDiscoveryStation> findAll() {
        return query("""
                                SELECT c.*
                                FROM discovery_station_cache c
                                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                                WHERE p.reachable = true AND p.blocked = false
                                ORDER BY c.fetched_at DESC;""").single(call()).map(CachedDiscoveryStation.map()).all();
    }

    public List<CachedDiscoveryStation> findForPeer(String instancePublicKey) {
        return query(
                        "SELECT * FROM discovery_station_cache WHERE instance_public_key = :instance_public_key ORDER BY fetched_at DESC;")
                .single(call().bind("instance_public_key", instancePublicKey))
                .map(CachedDiscoveryStation.map())
                .all();
    }

    /**
     * Editor's PARTNER_STATIONS picker (concept §3.9 / §4.5). Searches the discovery cache by
     * the cached card's name, country, or city — all case-insensitive. Only stations on
     * reachable, non-blocked peers are returned; the cache only contains public stations to
     * begin with (the `/public/discovery/stations` source endpoint excludes private ones), so
     * every result is selectable. Empty {@code search} returns the most recently fetched rows.
     */
    /** Look up cached station cards by their UUIDs (across all reachable peers). */
    public List<CachedDiscoveryStation> findByStationUids(List<String> stationUids) {
        if (stationUids == null || stationUids.isEmpty()) return List.of();
        return query("""
                                SELECT c.*
                                FROM discovery_station_cache c
                                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                                WHERE p.reachable = true AND p.blocked = false
                                  AND c.station_uid = ANY(:uids);""")
                .single(call().bind("uids", stationUids, PostgreSqlTypes.VARCHAR))
                .map(CachedDiscoveryStation.map())
                .all();
    }

    public List<CachedDiscoveryStation> searchForPicker(String search, int limit) {
        boolean hasSearch = search != null && !search.isBlank();
        String predicate = hasSearch ? """
                AND (LOWER(c.payload->>'name') LIKE :q
                     OR LOWER(COALESCE(c.payload->>'city', '')) LIKE :q
                     OR LOWER(COALESCE(c.payload->>'country', '')) LIKE :q)""" : "";
        var c = call().bind("limit", limit);
        if (hasSearch) {
            c = c.bind("q", "%" + search.trim().toLowerCase() + "%");
        }
        return query("""
                SELECT c.*
                FROM discovery_station_cache c
                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                WHERE p.reachable = true
                  AND p.blocked = false
                  %s
                ORDER BY c.fetched_at DESC
                LIMIT :limit;""", predicate).single(c).map(CachedDiscoveryStation.map()).all();
    }
}
