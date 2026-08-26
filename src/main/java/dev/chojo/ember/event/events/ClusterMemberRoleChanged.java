/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * What a cluster member may do has changed, whether by their type, a grant of their own, or a group they were
 * put in or taken out of.
 *
 * <p>One event for all four, because from the reader's side they are the same thing: what they can reach
 * tomorrow is not what they could reach today, and which of the three routes carried the change is an
 * implementation detail they never see.
 *
 * @param clusterMemberId the member whose standing moved
 * @param clusterName     the cluster, for the reader
 */
public record ClusterMemberRoleChanged(int clusterMemberId, String clusterName) implements DomainEvent {
    /**
     * Cluster-level, so no station is involved. The interface asks for one anyway, and zero is what it defines
     * as "no station".
     */
    @Override
    public int stationId() {
        return 0;
    }
}
