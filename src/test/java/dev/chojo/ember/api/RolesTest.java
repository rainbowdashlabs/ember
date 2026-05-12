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
                "ATTENDENCE_MANAGEMENT",
                "ATTENDENCE_EXPORT_MANAGER",
                "INVENTORY_MANAGEMENT",
                "EVENT_MANAGEMENT",
                "MEMBER_MANAGEMENT",
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
        assertTrue(expanded.contains(Roles.MEMBER_MANAGEMENT));
        assertTrue(expanded.contains(Roles.ATTENDENCE_MANAGEMENT));
        assertTrue(expanded.contains(Roles.INVENTORY_MANAGEMENT));
        assertTrue(expanded.contains(Roles.EVENT_MANAGEMENT));
    }
}
