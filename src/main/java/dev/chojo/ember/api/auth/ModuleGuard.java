/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.refusal.GeneralRefusal;
import dev.chojo.ember.feature.station.entity.StationModule;
import dev.chojo.ember.feature.station.service.ModuleAvailability;
import io.javalin.security.RouteRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.EnumSet;
import java.util.Set;

/**
 * Refuses every route of a module the station does not have, whether the station switched it off or its
 * cluster denies it.
 *
 * <p>A route belongs to a module in one of two ways. It declares the module among its roles, which is
 * what a route open to every member does, and then it belongs to exactly the modules it declares and is
 * refused when any of them is off. Or every permission it asks for names a module, and then it is refused
 * only when all of those are off, because any one of them would have let the caller in. A route asking
 * for a permission of no module and declaring none belongs to no module.
 */
@Singleton
public class ModuleGuard {
    private final ModuleAvailability modules;

    @Inject
    public ModuleGuard(ModuleAvailability modules) {
        this.modules = modules;
    }

    /**
     * The modules a route belongs to, read from its roles.
     *
     * @param roles    the roles the route declares
     * @param declared whether the modules were declared, which means every one of them has to be on,
     *                 rather than read from the permissions, which means one of them is enough
     * @param modules  the modules, empty for a route of no module
     */
    public record RouteModules(boolean declared, Set<StationModule> modules) {

        /**
         * Reads the modules a route belongs to from its roles.
         *
         * @param roles the route's roles
         * @return its modules
         */
        public static RouteModules of(Set<RouteRole> roles) {
            Set<StationModule> declaredModules = EnumSet.noneOf(StationModule.class);
            Set<StationModule> permissionModules = EnumSet.noneOf(StationModule.class);
            boolean everyPermissionNamesOne = true;
            boolean anyPermission = false;
            for (RouteRole role : roles) {
                if (role instanceof StationModule module) {
                    declaredModules.add(module);
                } else if (role instanceof StationPermission permission) {
                    anyPermission = true;
                    var module = permission.module();
                    if (module.isPresent()) permissionModules.add(module.get());
                    else everyPermissionNamesOne = false;
                }
            }
            if (!declaredModules.isEmpty()) return new RouteModules(true, declaredModules);
            if (anyPermission && everyPermissionNamesOne) return new RouteModules(false, permissionModules);
            return new RouteModules(false, Set.of());
        }

        /**
         * Whether the route answers, given the modules that are off.
         *
         * @param disabled the switched-off modules, with their parts
         * @return {@code true} when the route belongs to no module or its modules are on as it needs them
         */
        public boolean availableWith(Set<StationModule> disabled) {
            if (modules.isEmpty()) return true;
            if (declared) return modules.stream().noneMatch(module -> module.isOffIn(disabled));
            return modules.stream().anyMatch(module -> !module.isOffIn(disabled));
        }
    }

    /**
     * Refuses the request when the route belongs to a module the session's station does not have. A
     * session at no station is left to the route's other checks.
     *
     * @param roles   the roles the route declares
     * @param session the admitted session
     */
    public void require(Set<RouteRole> roles, UserSession session) {
        Integer stationId = session.stationId();
        if (stationId == null) return;
        RouteModules route = RouteModules.of(roles);
        if (route.modules().isEmpty()) return;
        if (!route.availableWith(modules.disabledAt(stationId))) {
            throw GeneralRefusal.MODULE_SWITCHED_OFF.raise();
        }
    }
}
