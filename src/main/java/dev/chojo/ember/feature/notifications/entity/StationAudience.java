/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.api.auth.StationPermission;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Station members a notification goes to, as parts the database unites when it writes the rows.
 *
 * <p>Built with the factories and combined with {@link #and} and {@link #except}. Whoever the parts
 * reach is told once, however many parts reach them; members who have left the station are never
 * told, and a ward who has left brings in no guardians.
 *
 * @param memberIds       members named one by one
 * @param wholeStationId  the station whose every member is meant, or {@code null}
 * @param holdersStationId the station whose holders of {@code permissions} are meant, or {@code null}
 * @param permissions     the permissions whose holders are meant, empty where none are
 * @param wardIds         members whose guardians are meant
 * @param excluded        members left out whatever else reaches them, usually whoever acted
 */
public record StationAudience(
        Set<Integer> memberIds,
        Integer wholeStationId,
        Integer holdersStationId,
        Set<StationPermission> permissions,
        Set<Integer> wardIds,
        Set<Integer> excluded)
        implements Audience {

    public StationAudience {
        memberIds = Set.copyOf(memberIds);
        permissions = Set.copyOf(permissions);
        wardIds = Set.copyOf(wardIds);
        excluded = Set.copyOf(excluded);
    }

    /** One member. */
    public static StationAudience member(int memberId) {
        return members(Set.of(memberId));
    }

    /** Members named one by one. */
    public static StationAudience members(Collection<Integer> memberIds) {
        return new StationAudience(Set.copyOf(memberIds), null, null, Set.of(), Set.of(), Set.of());
    }

    /** Every member of a station. */
    public static StationAudience wholeStation(int stationId) {
        return new StationAudience(Set.of(), stationId, null, Set.of(), Set.of(), Set.of());
    }

    /** The members of a station who hold a permission, by the same rule the permission checks use. */
    public static StationAudience holders(int stationId, StationPermission permission) {
        return new StationAudience(Set.of(), null, stationId, Set.of(permission), Set.of(), Set.of());
    }

    /** The guardians of the given members, not the members themselves. */
    public static StationAudience guardiansOf(Collection<Integer> wardIds) {
        return new StationAudience(Set.of(), null, null, Set.of(), Set.copyOf(wardIds), Set.of());
    }

    /** The given members together with their guardians. */
    public static StationAudience household(Collection<Integer> memberIds) {
        return members(memberIds).and(guardiansOf(memberIds));
    }

    /**
     * The members who may open a restricted entity.
     *
     * @param stationId  the station the entity belongs to
     * @param restricted the members who may open it, or empty where every member of the station may
     */
    public static StationAudience visibleTo(int stationId, Optional<Set<Integer>> restricted) {
        return restricted.map(StationAudience::members).orElseGet(() -> wholeStation(stationId));
    }

    /**
     * Everybody this audience or the other one reaches.
     *
     * @throws IllegalArgumentException when the two name different stations for the same part
     */
    public StationAudience and(StationAudience other) {
        return new StationAudience(
                union(memberIds, other.memberIds),
                sameStation(wholeStationId, other.wholeStationId),
                sameStation(holdersStationId, other.holdersStationId),
                union(permissions, other.permissions),
                union(wardIds, other.wardIds),
                union(excluded, other.excluded));
    }

    /**
     * This audience without one member.
     *
     * @param actorMemberId the member to leave out, or {@code null} to leave out nobody
     */
    public StationAudience except(Integer actorMemberId) {
        if (actorMemberId == null) return this;
        return and(new StationAudience(Set.of(), null, null, Set.of(), Set.of(), Set.of(actorMemberId)));
    }

    /** Whether no part reaches anybody, so writing can be skipped altogether. */
    public boolean reachesNobody() {
        return memberIds.isEmpty() && wholeStationId == null && holdersStationId == null && wardIds.isEmpty();
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        var all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }

    private static Integer sameStation(Integer first, Integer second) {
        if (first == null) return second;
        if (second != null && !Objects.equals(first, second)) {
            throw new IllegalArgumentException(
                    "A part of an audience spans one station, not " + first + " and " + second);
        }
        return first;
    }
}
