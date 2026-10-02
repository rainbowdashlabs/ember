/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.discovery.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.discovery.entity.BlocklistKind;
import dev.chojo.ember.feature.discovery.entity.CachedDiscoveryStation;
import dev.chojo.ember.feature.discovery.entity.DiscoveryPeer;
import dev.chojo.ember.feature.discovery.entity.DiscoveryStationCard;
import dev.chojo.ember.feature.discovery.entity.PublishedRemoteStation;
import dev.chojo.ember.util.sql.WhereBuilder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static dev.chojo.ember.util.sql.SqlSupport.alias;

@Singleton
public class DiscoveryStationCacheRepository {
    private static final String DISCOVERY_STATION_CACHE_COLUMNS =
            "instance_public_key, station_uid, payload, fetched_at";

    /**
     * Inserts or refreshes a cached station card for a peer.
     */
    public void upsert(String instancePublicKey, DiscoveryStationCard card, Instant fetchedAt) {
        query("""
                INSERT INTO discovery_station_cache (instance_public_key, station_uid, payload, fetched_at)
                VALUES (:instance_public_key, :station_uid, :payload::JSONB, :fetched_at)
                ON CONFLICT (instance_public_key, station_uid) DO UPDATE
                SET payload    = excluded.payload,
                    fetched_at = excluded.fetched_at;""")
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
                SELECT %s
                FROM discovery_station_cache c
                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                WHERE p.reachable = TRUE AND p.blocked = FALSE
                ORDER BY c.fetched_at DESC;""", alias("c", DISCOVERY_STATION_CACHE_COLUMNS))
                .single(call())
                .map(CachedDiscoveryStation.map())
                .all();
    }

    /**
     * The station cards other instances publish, as the public discovery page lists them.
     *
     * <p>Left out are cards of peers that are unreachable, blocked, distrusted by their reputation or
     * on the blocklist by key or by address, cards this instance appears to have published itself, and
     * cards naming a station of this instance, which the page already lists as a local one.
     *
     * @param ownPublicKey this instance's public key
     * @param ownBaseUrl   the address this instance publishes as its own
     * @return the cards by station name, then by instance address
     */
    public List<PublishedRemoteStation> findPublishedElsewhere(String ownPublicKey, String ownBaseUrl) {
        return query("""
                SELECT c.instance_public_key, p.base_url, c.payload
                FROM discovery_station_cache c
                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                WHERE p.reachable = TRUE
                  AND p.blocked = FALSE
                  AND p.reputation > :distrusted
                  AND p.public_key <> :own_key
                  AND p.base_url <> :own_base_url
                  AND NOT EXISTS (
                      SELECT 1
                      FROM discovery_blocklist b
                      WHERE (b.kind = :key_kind AND b.value = p.public_key)
                         OR (b.kind = :url_kind AND b.value = p.base_url))
                  AND NOT EXISTS (
                      SELECT 1
                      FROM station s
                      WHERE s.uid::TEXT = c.station_uid)
                ORDER BY LOWER(c.payload ->> 'name'), p.base_url, c.station_uid;""")
                .single(call().bind("distrusted", DiscoveryPeer.DISTRUSTED_REPUTATION)
                        .bind("own_key", ownPublicKey)
                        .bind("own_base_url", ownBaseUrl)
                        .bind("key_kind", BlocklistKind.PUBLIC_KEY.name())
                        .bind("url_kind", BlocklistKind.BASE_URL.name()))
                .map(PublishedRemoteStation.map())
                .all();
    }

    public List<CachedDiscoveryStation> findForPeer(String instancePublicKey) {
        return query(
                        "SELECT %s FROM discovery_station_cache WHERE instance_public_key = :instance_public_key ORDER BY fetched_at DESC;",
                        DISCOVERY_STATION_CACHE_COLUMNS)
                .single(call().bind("instance_public_key", instancePublicKey))
                .map(CachedDiscoveryStation.map())
                .all();
    }

    /**
     * Look up cached station cards by their UUIDs (across all reachable peers).
     */
    public List<CachedDiscoveryStation> findByStationUids(List<String> stationUids) {
        if (stationUids == null || stationUids.isEmpty()) return List.of();
        return query("""
                SELECT %s
                FROM discovery_station_cache c
                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                WHERE p.reachable = TRUE AND p.blocked = FALSE
                  AND c.station_uid = ANY(:uids);""", alias("c", DISCOVERY_STATION_CACHE_COLUMNS))
                .single(call().bind("uids", stationUids, PostgreSqlTypes.VARCHAR))
                .map(CachedDiscoveryStation.map())
                .all();
    }

    public List<CachedDiscoveryStation> searchForPicker(@Nullable String search, int limit) {
        var where = WhereBuilder.create().like("""
                        AND (LOWER(c.payload->>'name') LIKE :q
                             OR LOWER(COALESCE(c.payload->>'city', '')) LIKE :q
                             OR LOWER(COALESCE(c.payload->>'country', '')) LIKE :q)""", "q", search);
        return query("""
                SELECT %s
                FROM discovery_station_cache c
                JOIN discovery_peer p ON p.public_key = c.instance_public_key
                WHERE p.reachable = TRUE
                  AND p.blocked = FALSE
                  %s
                ORDER BY C.fetched_at DESC
                LIMIT :limit;""", alias("c", DISCOVERY_STATION_CACHE_COLUMNS), where.fragment())
                .single(where.apply(call().bind("limit", limit)))
                .map(CachedDiscoveryStation.map())
                .all();
    }
}
