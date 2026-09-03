/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;

import java.util.HashSet;
import java.util.Set;

/**
 * Which profile fields a reader may see, worked out from what they may do.
 *
 * <p>A field carries a scope rather than a permission, so every screen that shows one has to map the
 * reader's permissions onto scopes the same way. Doing that in each screen is how two of them come to
 * disagree, and a field kept from somebody on one page and shown on another is not kept from them at
 * all.
 */
public final class ProfileFieldScopes {

    /**
     * The permissions that make somebody part of the team, as opposed to a member or a guardian.
     */
    private static final Set<StationPermission> TEAM_PERMISSIONS = Set.of(
            StationPermission.STATION_ADMINISTRATOR,
            StationPermission.ATTENDANCE_MANAGER,
            StationPermission.INVENTORY_MANAGER,
            StationPermission.EVENT_MANAGER,
            StationPermission.MEMBER_MANAGER);

    private ProfileFieldScopes() {}

    /**
     * The scopes a reader holding these permissions may read.
     *
     * <p>{@link ProfileFieldScope#GROUP} is never among them: a field scoped to a group is answered
     * by who is in that group and not by what anybody may do, so it is decided elsewhere.
     *
     * @param permissions the reader's permissions, already expanded
     * @return the scopes they may read
     */
    public static Set<ProfileFieldScope> readableBy(Set<StationPermission> permissions) {
        var scopes = new HashSet<ProfileFieldScope>();
        if (permissions.contains(StationPermission.USER)) scopes.add(ProfileFieldScope.MEMBER);
        if (permissions.contains(StationPermission.MEMBER_GUARDIAN)) scopes.add(ProfileFieldScope.GUARDIAN);
        if (permissions.stream().anyMatch(TEAM_PERMISSIONS::contains)) scopes.add(ProfileFieldScope.TEAM);
        if (permissions.contains(StationPermission.STATION_ADMINISTRATOR)) scopes.add(ProfileFieldScope.MANAGER);
        return scopes;
    }
}
