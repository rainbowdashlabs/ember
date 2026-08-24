/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A named set of an association's stations.
 *
 * <p>A filing rather than a partition: a station sits in a regional group and an equipment group at once,
 * because those are two different questions about it. What it is for is pointing a question at some of the
 * stations rather than all of them.
 *
 * @param id        the internal identifier
 * @param clusterId the association doing the filing
 * @param name      the label, unique within its association
 */
public record ClusterStationGroup(int id, int clusterId, String name) {

    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterStationGroup> map() {
        return row -> new ClusterStationGroup(row.getInt("id"), row.getInt("cluster_id"), row.getString("name"));
    }
}
