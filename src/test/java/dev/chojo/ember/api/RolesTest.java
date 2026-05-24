/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RolesTest {

    @Test
    void managerIncludesTeamWhenExpanded() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.MANAGER));
        assertTrue(expanded.contains(Roles.TEAM), "MANAGER should include TEAM");
    }

    @Test
    void managerIncludesAllManagementRoles() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.MANAGER));
        assertTrue(expanded.contains(Roles.ATTENDANCE_MANAGER));
        assertTrue(expanded.contains(Roles.ATTENDANCE_EXPORT_MANAGER));
        assertTrue(expanded.contains(Roles.INVENTORY_MANAGER));
        assertTrue(expanded.contains(Roles.EVENT_MANAGER));
        assertTrue(expanded.contains(Roles.MEMBER_MANAGER));
        assertTrue(expanded.contains(Roles.NEWS_MANAGER));
        assertTrue(expanded.contains(Roles.POLL_MANAGER));
        assertTrue(expanded.contains(Roles.LOST_AND_FOUND_MANAGER));
        assertTrue(expanded.contains(Roles.WAITLIST_MANAGER));
        assertTrue(expanded.contains(Roles.QUIZ_MANAGER));
        assertTrue(expanded.contains(Roles.KNOWLEDGE_MANAGER));
        assertTrue(expanded.contains(Roles.PROTOCOL_MANAGER));
        assertTrue(expanded.contains(Roles.PROTOCOL_TESTER));
        assertTrue(expanded.contains(Roles.FEDERATION_MANAGER));
    }

    @Test
    void managerIncludesTransitiveChildrenOfTeam() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.MANAGER));
        // TEAM includes LOGIN and USER
        assertTrue(expanded.contains(Roles.LOGIN));
        assertTrue(expanded.contains(Roles.USER));
    }

    @Test
    void managementRolesDoNotIncludeTeam() {
        // Individual management roles have no children
        for (var role : new Roles[]{
                Roles.ATTENDANCE_MANAGER, Roles.INVENTORY_MANAGER,
                Roles.EVENT_MANAGER, Roles.MEMBER_MANAGER}) {
            Set<Roles> expanded = Roles.expand(EnumSet.of(role));
            assertFalse(expanded.contains(Roles.TEAM),
                    role + " should NOT include TEAM");
            assertEquals(1, expanded.size(),
                    role + " should only contain itself");
        }
    }

    @Test
    void teamIncludesLoginAndUser() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.TEAM));
        assertTrue(expanded.contains(Roles.LOGIN));
        assertTrue(expanded.contains(Roles.USER));
    }

    @Test
    void guardianIncludesLoginAndUser() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.GUARDIAN));
        assertTrue(expanded.contains(Roles.LOGIN));
        assertTrue(expanded.contains(Roles.USER));
    }

    @Test
    void trialIncludesLoginAndUser() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.TRIAL));
        assertTrue(expanded.contains(Roles.LOGIN));
        assertTrue(expanded.contains(Roles.USER));
    }

    @Test
    void memberIncludesUser() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.MEMBER));
        assertTrue(expanded.contains(Roles.USER));
    }

    @Test
    void adminHasNoChildren() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.ADMIN));
        assertEquals(1, expanded.size(), "ADMIN should only contain itself");
        assertTrue(expanded.contains(Roles.ADMIN));
    }
}
