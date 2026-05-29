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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves member IDs to UUIDs and vice versa within a station scope.
 * Caches lookups for performance since member UIDs never change.
 */
@Singleton
public class MemberUidResolver {
    private static final MemberUidResolver INSTANCE = new MemberUidResolver();

    private final Map<Integer, UUID> idToUid = new ConcurrentHashMap<>();
    private final Map<StationMemberUid, Integer> uidToId = new ConcurrentHashMap<>();

    public static MemberUidResolver instance() {
        return INSTANCE;
    }

    /**
     * Resolves an internal member ID to its UUID.
     * Returns null if the member does not exist.
     */
    public UUID resolve(int memberId) {
        return idToUid.computeIfAbsent(memberId, id -> Query.query("SELECT uid FROM station_member WHERE id = :id;")
                .single(Call.of().bind("id", id))
                .map(row -> row.get("uid", StandardValueConverter.UUID_STRING))
                .first()
                .orElse(null));
    }

    /**
     * Resolves a member UUID within a station to its internal integer ID.
     * Returns empty if the member does not exist.
     */
    public Optional<Integer> resolve(int stationId, UUID uid) {
        var key = new StationMemberUid(stationId, uid);
        Integer cached = uidToId.get(key);
        if (cached != null) return Optional.of(cached);

        return Query.query("SELECT id FROM station_member WHERE station_id = :station_id AND uid = :uid;")
                .single(Call.of().bind("station_id", stationId).bind("uid", uid, StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .map(id -> {
                    uidToId.put(key, id);
                    idToUid.put(id, uid);
                    return id;
                });
    }

    /**
     * Resolves an internal member ID to its UUID string.
     * Returns null if the member does not exist.
     */
    public String resolveToString(int memberId) {
        UUID uid = resolve(memberId);
        return uid != null ? uid.toString() : null;
    }

    public void invalidate(int memberId) {
        UUID uid = idToUid.remove(memberId);
        if (uid != null) {
            uidToId.values().remove(memberId);
        }
    }

    public void clearCache() {
        idToUid.clear();
        uidToId.clear();
    }

    private record StationMemberUid(int stationId, UUID uid) {}
}
