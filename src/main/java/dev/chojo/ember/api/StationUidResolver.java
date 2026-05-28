/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves internal integer station IDs to external UUIDs.
 * Caches lookups for performance since station UIDs never change.
 */
@Singleton
public class StationUidResolver {
    private static final StationUidResolver INSTANCE = new StationUidResolver();

    private final Map<Integer, UUID> cache = new ConcurrentHashMap<>();

    public static StationUidResolver instance() {
        return INSTANCE;
    }

    /**
     * Resolves an internal station ID to its external UUID.
     * Returns null if the station does not exist.
     */
    public UUID resolve(int stationId) {
        return cache.computeIfAbsent(stationId, id -> Query.query("SELECT uid FROM station WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(row -> row.get("uid", StandardValueConverter.UUID_STRING))
                .first()
                .orElse(null));
    }

    /**
     * Resolves an internal station ID to its external UUID string.
     * Returns null if the station does not exist.
     */
    public String resolveToString(int stationId) {
        UUID uid = resolve(stationId);
        return uid != null ? uid.toString() : null;
    }

    /**
     * Removes a specific entry from the cache.
     */
    public void invalidate(int stationId) {
        cache.remove(stationId);
    }

    /**
     * Clears the entire cache. Used in tests to prevent stale entries between test classes.
     */
    public void clearCache() {
        cache.clear();
    }
}
