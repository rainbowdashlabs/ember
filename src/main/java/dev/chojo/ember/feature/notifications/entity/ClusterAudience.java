/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

import dev.chojo.ember.api.auth.ClusterPermission;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Cluster members a notification goes to.
 *
 * <p>Holders of a cluster permission are named by the permission and resolved when the notification
 * is written, because the expansion of a cluster user type's defaults lives in the enum rather than
 * in the database. Cluster members have no per-type settings, so nobody is left out for a preference.
 *
 * @param memberIds        cluster members named one by one
 * @param holdersClusterId the cluster whose holders of {@code permissions} are meant, or {@code null}
 * @param permissions      the permissions whose holders are meant, empty where none are
 * @param excluded         cluster members left out whatever else reaches them
 */
public record ClusterAudience(
        Set<Integer> memberIds, Integer holdersClusterId, Set<ClusterPermission> permissions, Set<Integer> excluded)
        implements Audience {

    public ClusterAudience {
        memberIds = Set.copyOf(memberIds);
        permissions = Set.copyOf(permissions);
        excluded = Set.copyOf(excluded);
    }

    /** Cluster members named one by one. */
    public static ClusterAudience members(Collection<Integer> clusterMemberIds) {
        return new ClusterAudience(Set.copyOf(clusterMemberIds), null, Set.of(), Set.of());
    }

    /** The members of a cluster who hold a permission. */
    public static ClusterAudience holders(int clusterId, ClusterPermission permission) {
        return new ClusterAudience(Set.of(), clusterId, Set.of(permission), Set.of());
    }

    /**
     * Everybody this audience or the other one reaches.
     *
     * @throws IllegalArgumentException when the two ask for holders at different clusters
     */
    public ClusterAudience and(ClusterAudience other) {
        if (holdersClusterId != null
                && other.holdersClusterId != null
                && !Objects.equals(holdersClusterId, other.holdersClusterId)) {
            throw new IllegalArgumentException(
                    "Holders of one cluster only, not " + holdersClusterId + " and " + other.holdersClusterId);
        }
        return new ClusterAudience(
                union(memberIds, other.memberIds),
                holdersClusterId != null ? holdersClusterId : other.holdersClusterId,
                union(permissions, other.permissions),
                union(excluded, other.excluded));
    }

    /**
     * This audience without one cluster member.
     *
     * @param actorClusterMemberId the cluster member to leave out, or {@code null} to leave out nobody
     */
    public ClusterAudience except(Integer actorClusterMemberId) {
        if (actorClusterMemberId == null) return this;
        return and(new ClusterAudience(Set.of(), null, Set.of(), Set.of(actorClusterMemberId)));
    }

    private static <T> Set<T> union(Set<T> first, Set<T> second) {
        var all = new HashSet<>(first);
        all.addAll(second);
        return all;
    }
}
