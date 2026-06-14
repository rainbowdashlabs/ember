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
}
