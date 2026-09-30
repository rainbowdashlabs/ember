/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.repository;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Reads and writes the stored form of a station's federation signing key.
 *
 * <p>Nothing here knows whether the stored value is encrypted: the column holds whatever the caller
 * hands over, and it is the caller that seals and unseals it. The station entity does not carry the
 * column at all, so the key is read exactly where it is needed and nowhere else.
 */
@Singleton
public class StationKeyRepository {

    /**
     * The stored key of a station, if it has one.
     *
     * @param stationId the station
     * @return the stored value, or empty when the station has no key yet
     */
    public Optional<String> find(int stationId) {
        return query("SELECT federation_private_key FROM station WHERE id = :id;")
                .single(call().bind("id", stationId))
                .map(row -> row.getString("federation_private_key"))
                .first()
                .filter(key -> !key.isBlank());
    }

    /**
     * Stores a key for a station that has none, leaving an existing one untouched.
     *
     * @param stationId the station
     * @param stored    the value to store
     * @return true when the value was written, false when the station already had a key
     */
    public boolean storeIfAbsent(int stationId, String stored) {
        return query("""
                        UPDATE station
                        SET federation_private_key = :key
                        WHERE id = :id
                          AND (federation_private_key IS NULL OR federation_private_key = '');
                        """)
                .single(call().bind("key", stored).bind("id", stationId))
                .update()
                .changed();
    }

    /**
     * Replaces a station's key, whatever it held before.
     *
     * @param stationId the station
     * @param stored    the value to store
     */
    public void replace(int stationId, String stored) {
        query("UPDATE station SET federation_private_key = :key WHERE id = :id;")
                .single(call().bind("key", stored).bind("id", stationId))
                .update();
    }

    /**
     * Replaces a station's key only while it still holds the value the caller read, so a conversion
     * racing another writer never overwrites what that writer stored.
     *
     * @param stationId the station
     * @param expected  the value the caller read
     * @param stored    the value to store instead
     * @return true when the value was replaced
     */
    public boolean replaceIfUnchanged(int stationId, String expected, String stored) {
        return query("""
                        UPDATE station
                        SET federation_private_key = :key
                        WHERE id = :id
                          AND federation_private_key = :expected;
                        """)
                .single(call().bind("key", stored).bind("id", stationId).bind("expected", expected))
                .update()
                .changed();
    }

    /**
     * Every station whose key is stored without the given prefix, which is how a key written before
     * encryption at rest is recognised.
     *
     * @param sealedPrefix the prefix every encrypted value starts with
     * @return the stations and their stored values
     */
    public List<StoredKey> findWithoutPrefix(String sealedPrefix) {
        return query("""
                        SELECT id, federation_private_key
                        FROM station
                        WHERE federation_private_key IS NOT NULL
                          AND federation_private_key <> ''
                          AND NOT starts_with(federation_private_key, :prefix)
                        ORDER BY id;
                        """)
                .single(call().bind("prefix", sealedPrefix))
                .map(row -> new StoredKey(row.getInt("id"), row.getString("federation_private_key")))
                .all();
    }

    /**
     * A station's key as the column holds it.
     *
     * @param stationId the station
     * @param stored    the stored value
     */
    public record StoredKey(int stationId, String stored) {}
}
