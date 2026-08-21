/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named set of permissions several cluster members can be put in at once.
 *
 * <p>The third way a cluster member comes to hold a permission, beside their user type's defaults and the
 * grants made to them by name. It exists for the same reason the station's groups do: a cluster with a dozen
 * people running its gear should be able to say that once rather than a dozen times.
 *
 * @param id        the group
 * @param clusterId the cluster it belongs to
 * @param name      what it is called, unique within its cluster
 */
public record ClusterMemberGroup(int id, int clusterId, String name) {

    public static RowMapping<ClusterMemberGroup> map() {
        return row -> new ClusterMemberGroup(row.getInt("id"), row.getInt("cluster_id"), row.getString("name"));
    }
}
