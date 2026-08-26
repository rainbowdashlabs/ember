/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

/**
 * A fixed set of permissions a cluster member carries by default. One per member, and the type itself grants
 * nothing: what it means is entirely the default permissions listed here.
 *
 * <p>An account may hold a different type in each cluster it belongs to, and a station type on top of that.
 *
 * <p><b>Nothing at a station ever makes somebody a cluster member.</b> Belonging to a station under a
 * cluster does not, running that station does not, and administering it does not either: a membership here
 * is only ever written by somebody who already acts for the cluster, or by the instance handing a new
 * cluster its first person. The type is for people with a job in running the cluster, and everybody else at
 * every station under it belongs to their station and to nothing else.
 */
public enum ClusterUserType {
    /**
     * Somebody the cluster has taken on to do a job there, carrying nothing beyond being here until the job
     * is granted.
     *
     * <p>Being a member is itself what {@code LOGIN} and the {@code USER} it expands to mean: the cluster's
     * own name, the stations under it, what it publishes and one's own notifications from it, and not one
     * thing it governs. Exactly as a station member holds its {@code LOGIN} for belonging. Without it
     * somebody granted one narrow right
     * was offered the cluster everywhere in the shell and refused its every page, which reads as a broken
     * application rather than as a permission doing its job.
     */
    CLUSTER_USER(ClusterPermission.LOGIN),
    /**
     * Somebody who runs the cluster.
     */
    CLUSTER_ADMIN(ClusterPermission.CLUSTER_ADMINISTRATOR, ClusterPermission.LOGIN);

    private final ClusterPermission[] defaultPermissions;

    ClusterUserType(ClusterPermission... defaultPermissions) {
        this.defaultPermissions = defaultPermissions;
    }

    public ClusterPermission[] defaultPermissions() {
        return defaultPermissions;
    }
}
