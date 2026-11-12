/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.auth.ModuleGuard.RouteModules;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.security.RouteRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which module a route belongs to, read from its roles, and when the guard lets it answer. */
class ModuleGuardTest {

    /** Every module and every part refuses a route that declares it while it, or its module, is off. */
    @ParameterizedTest
    @EnumSource(StationModule.class)
    void everyModuleClosesItsRoutes(StationModule module) {
        RouteModules route = RouteModules.of(Set.<RouteRole>of(StationPermission.USER, module));

        assertFalse(route.availableWith(Set.of(module)));
        assertFalse(route.availableWith(
                StationModule.withChildren(Set.of(module.parent().orElse(module)))));
        assertTrue(route.availableWith(Set.of()));
        for (StationModule other : StationModule.values()) {
            if (!module.belongsTo(other)) assertTrue(route.availableWith(Set.of(other)), other.name());
        }
    }

    /** Declared modules all have to be on. */
    @Test
    void declaredModulesAllHaveToBeOn() {
        RouteModules route = RouteModules.of(
                Set.<RouteRole>of(StationPermission.EVENT_EDIT, StationModule.EVENTS, StationModule.INVENTORY));

        assertTrue(route.declared());
        assertEquals(Set.of(StationModule.EVENTS, StationModule.INVENTORY), route.modules());
        assertFalse(route.availableWith(Set.of(StationModule.INVENTORY)));
        assertTrue(route.availableWith(Set.of(StationModule.NEWS)));
    }

    /** Modules read from the permissions close the route only when every one of them is off. */
    @Test
    void permissionModulesCloseOnlyWhenAllAreOff() {
        RouteModules route =
                RouteModules.of(Set.<RouteRole>of(StationPermission.ATTENDANCE_READ, StationPermission.EVENT_EDIT));

        assertFalse(route.declared());
        assertTrue(route.availableWith(Set.of(StationModule.EVENTS)));
        assertFalse(route.availableWith(Set.of(StationModule.EVENTS, StationModule.ATTENDANCE)));
    }

    /** A permission of no module, alone or beside module permissions, leaves the route under no module. */
    @Test
    void aPermissionOfNoModuleLeavesTheRouteFree() {
        assertTrue(RouteModules.of(Set.<RouteRole>of(StationPermission.USER))
                .modules()
                .isEmpty());
        assertTrue(RouteModules.of(Set.<RouteRole>of(StationPermission.MEMBER_EDIT, StationPermission.ATTENDANCE_EDIT))
                .modules()
                .isEmpty());
        assertTrue(RouteModules.of(Set.of()).availableWith(Set.of(StationModule.NEWS)));
    }
}
