/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.roles;

/**
 * A user type represents a fixed set of permissions
 * Only one user type can be assigned to a user per station.
 * The user type itself should not grant any access at all. This is only defined by the granted default permissions.
 * One account might have multiple user types on different stations.
 */
public enum StationUserType {
    /**
     * A user on a trial.
     * Usually cannot log in or be managed. It will be converted into a member upon joining.
     */
    TRIAL,
    /**
     * The default user type for members of a station. Usually managed by a {@link #GUARDIAN}
     */
    MEMBER(StationPermission.USER),
    /**
     * A user that manages a {@link #MEMBER}
     */
    GUARDIAN(StationPermission.LOGIN, StationPermission.MEMBER_GUARDIAN),
    /**
     * A user that is a member of the team.
     */
    TEAM(StationPermission.LOGIN),
    /**
     * A user that manages the station.
     */
    MANAGER(StationPermission.STATION_ADMINISTRATOR, StationPermission.LOGIN);

    private final StationPermission[] defaultPermissions;

    StationUserType() {
        this.defaultPermissions = new StationPermission[0];
    }

    StationUserType(StationPermission... defaultPermissions) {
        this.defaultPermissions = defaultPermissions;
    }

    public StationPermission[] defaultPermissions() {
        return defaultPermissions;
    }
}
