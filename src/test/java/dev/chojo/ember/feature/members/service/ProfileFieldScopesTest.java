/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileFieldScopesTest {

    @Test
    void anOrdinaryMemberReadsOnlyTheMemberScope() {
        assertEquals(Set.of(ProfileFieldScope.MEMBER), ProfileFieldScopes.readableBy(Set.of(StationPermission.USER)));
    }

    @Test
    void lookingAfterSomebodyAddsTheGuardianScope() {
        var scopes = ProfileFieldScopes.readableBy(Set.of(StationPermission.USER, StationPermission.MEMBER_GUARDIAN));

        assertTrue(scopes.contains(ProfileFieldScope.GUARDIAN));
        assertFalse(scopes.contains(ProfileFieldScope.TEAM));
    }

    /**
     * Any one of the manager permissions makes somebody part of the team, so a field kept to the
     * team is readable by whoever runs the equipment as much as by whoever runs the members.
     */
    @Test
    void anyManagerPermissionMakesSomebodyPartOfTheTeam() {
        assertTrue(ProfileFieldScopes.readableBy(Set.of(StationPermission.INVENTORY_MANAGER))
                .contains(ProfileFieldScope.TEAM));
        assertTrue(ProfileFieldScopes.readableBy(Set.of(StationPermission.ATTENDANCE_MANAGER))
                .contains(ProfileFieldScope.TEAM));
        assertTrue(ProfileFieldScopes.readableBy(Set.of(StationPermission.EVENT_MANAGER))
                .contains(ProfileFieldScope.TEAM));
        assertTrue(ProfileFieldScopes.readableBy(Set.of(StationPermission.MEMBER_MANAGER))
                .contains(ProfileFieldScope.TEAM));
    }

    /**
     * The manager scope is the administrator's alone. Running one part of a station is not running
     * the station.
     */
    @Test
    void onlyTheAdministratorReadsTheManagerScope() {
        assertTrue(ProfileFieldScopes.readableBy(Set.of(StationPermission.STATION_ADMINISTRATOR))
                .contains(ProfileFieldScope.MANAGER));
        assertFalse(ProfileFieldScopes.readableBy(Set.of(StationPermission.INVENTORY_MANAGER))
                .contains(ProfileFieldScope.MANAGER));
    }

    /**
     * A group scope is answered by who is in the group rather than by what anybody may do, so it is
     * never handed out here.
     */
    @Test
    void theGroupScopeIsNeverDecidedByPermissions() {
        assertFalse(ProfileFieldScopes.readableBy(Set.of(StationPermission.STATION_ADMINISTRATOR))
                .contains(ProfileFieldScope.GROUP));
    }

    @Test
    void holdingNothingReadsNothing() {
        assertTrue(ProfileFieldScopes.readableBy(Set.of()).isEmpty());
    }
}
