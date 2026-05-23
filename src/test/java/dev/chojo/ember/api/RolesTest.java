/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RolesTest {

    @ParameterizedTest
    @EnumSource(
            value = Roles.class,
            names = {
                "ATTENDANCE_MANAGER",
                "ATTENDANCE_EXPORT_MANAGER",
                "INVENTORY_MANAGER",
                "EVENT_MANAGER",
                "MEMBER_MANAGER",
                "MANAGER",
                "ADMIN"
            })
    void roleWithTeamChildIncludesTeamWhenExpanded(Roles role) {
        Set<Roles> expanded = Roles.expand(EnumSet.of(role));
        assertTrue(expanded.contains(Roles.TEAM), role + " should include TEAM after expansion");
    }

    @Test
    void expandedRolesContainAllTransitiveChildren() {
        Set<Roles> expanded = Roles.expand(EnumSet.of(Roles.ADMIN));
        assertTrue(expanded.contains(Roles.MANAGER));
        assertTrue(expanded.contains(Roles.TEAM));
        assertTrue(expanded.contains(Roles.LOGIN));
        assertTrue(expanded.contains(Roles.USER));
        assertTrue(expanded.contains(Roles.MEMBER_MANAGER));
        assertTrue(expanded.contains(Roles.ATTENDANCE_MANAGER));
        assertTrue(expanded.contains(Roles.INVENTORY_MANAGER));
        assertTrue(expanded.contains(Roles.EVENT_MANAGER));
    }
}
