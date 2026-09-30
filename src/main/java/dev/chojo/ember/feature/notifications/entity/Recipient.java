/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.notifications.entity;

/**
 * Whose notifications are read: a station member's or a cluster member's.
 *
 * <p>The two are separate memberships with separate feeds, and one person can hold both. Naming the
 * kind in the type keeps a station member's id from ever reading a cluster member's feed.
 */
public sealed interface Recipient {

    /** A station member's feed. */
    static Recipient stationMember(int memberId) {
        return new OfStation(memberId);
    }

    /** A cluster member's feed. */
    static Recipient clusterMember(int clusterMemberId) {
        return new OfCluster(clusterMemberId);
    }

    /**
     * A station member.
     *
     * @param memberId the station member
     */
    record OfStation(int memberId) implements Recipient {}

    /**
     * A cluster member.
     *
     * @param clusterMemberId the cluster member
     */
    record OfCluster(int clusterMemberId) implements Recipient {}
}
