/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.roles.StationPermission;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RolesTest {

    @Test
    void stationAdministratorIncludesLoginWhenExpanded() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        assertTrue(expanded.contains(StationPermission.LOGIN), "STATION_ADMINISTRATOR should include LOGIN");
    }

    @Test
    void stationAdministratorIncludesAllManagementPermissions() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        assertTrue(expanded.contains(StationPermission.ATTENDANCE_MANAGER));
        assertTrue(expanded.contains(StationPermission.ATTENDANCE_EXPORT));
        assertTrue(expanded.contains(StationPermission.INVENTORY_MANAGER));
        assertTrue(expanded.contains(StationPermission.EVENT_MANAGER));
        assertTrue(expanded.contains(StationPermission.MEMBER_MANAGER));
        assertTrue(expanded.contains(StationPermission.NEWS_MANAGER));
        assertTrue(expanded.contains(StationPermission.POLL_MANAGER));
        assertTrue(expanded.contains(StationPermission.LOST_AND_FOUND_MANAGER));
        assertTrue(expanded.contains(StationPermission.WAITLIST_MANAGER));
        assertTrue(expanded.contains(StationPermission.TEST_MANAGER));
        assertTrue(expanded.contains(StationPermission.KNOWLEDGE_MANAGER));
        assertTrue(expanded.contains(StationPermission.PROTOCOL_MANAGER));
        assertTrue(expanded.contains(StationPermission.PROTOCOL_TESTER));
        assertTrue(expanded.contains(StationPermission.STATION_FEDERATION));
    }

    @Test
    void stationAdministratorIncludesTransitiveChildren() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.STATION_ADMINISTRATOR));
        // STATION_ADMINISTRATOR -> STATION_MANAGER -> includes LOGIN via hierarchy
        assertTrue(expanded.contains(StationPermission.LOGIN));
        assertTrue(expanded.contains(StationPermission.USER));
    }

    @Test
    void managementPermissionsDoNotIncludeLogin() {
        // Individual management permissions should not include LOGIN
        for (var perm : new StationPermission[] {
            StationPermission.ATTENDANCE_MANAGER, StationPermission.INVENTORY_MANAGER,
            StationPermission.EVENT_MANAGER, StationPermission.MEMBER_MANAGER
        }) {
            Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(perm));
            assertFalse(expanded.contains(StationPermission.LOGIN), perm + " should NOT include LOGIN");
        }
    }

    @Test
    void loginIncludesUser() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.LOGIN));
        assertTrue(expanded.contains(StationPermission.USER));
    }

    @Test
    void memberGuardianDoesNotIncludeLogin() {
        // MEMBER_GUARDIAN is a standalone permission, not a child of LOGIN
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.MEMBER_GUARDIAN));
        assertFalse(expanded.contains(StationPermission.LOGIN));
    }

    @Test
    void userExpandsToOnlyItself() {
        Set<StationPermission> expanded = StationPermission.expand(EnumSet.of(StationPermission.USER));
        assertTrue(expanded.contains(StationPermission.USER));
        assertEquals(1, expanded.size(), "USER should only contain itself");
    }
}
