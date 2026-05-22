/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import java.util.Arrays;

public enum RoleScope {
    SYSTEM(Roles.ADMIN),
    STATION_USER(Roles.MEMBER, Roles.GUARDIAN, Roles.TEAM, Roles.MANAGER),
    STATION_MANAGEMENT(Arrays.stream(Roles.values())
            .filter(role -> role.name().contains("MANAGEMENT") || role.name().contains("MANAGER"))
            .toArray(Roles[]::new));

    private final Roles[] roles;

    RoleScope(Roles... roles) {
        this.roles = roles;
    }
}
