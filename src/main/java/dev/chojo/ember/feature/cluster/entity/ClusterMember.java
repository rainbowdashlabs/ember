/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import dev.chojo.ember.api.auth.ClusterUserType;

/**
 * An account acting on a cluster's behalf.
 *
 * <p>There is no former-member state here as there is for stations: revoking a membership deletes the row.
 * An account may belong to several clusters at once, and to stations besides.
 *
 * @param id        the internal identifier
 * @param clusterId the cluster they belong to
 * @param accountId the account behind them
 * @param userType  their user type, which carries a set of permissions by default
 */
public record ClusterMember(int id, int clusterId, int accountId, ClusterUserType userType) {
    /**
     * Creates a row mapping for database result set conversion.
     */
    public static RowMapping<ClusterMember> map() {
        return row -> new ClusterMember(
                row.getInt("id"),
                row.getInt("cluster_id"),
                row.getInt("account_id"),
                row.getEnum("user_type", ClusterUserType.class));
    }
}
