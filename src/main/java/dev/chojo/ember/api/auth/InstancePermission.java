/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import io.javalin.security.RouteRole;

import java.util.EnumSet;
import java.util.Set;

/**
 * The permissions that are granted to the instance administrator.
 */
public enum InstancePermission implements RouteRole {
    ADMINISTRATOR;

    private final InstancePermission[] children;
    private Set<InstancePermission> allChildren;

    public InstancePermission[] getChildren() {
        return children;
    }

    InstancePermission(InstancePermission... children) {
        this.children = children;
    }

    /**
     * Expands a set of permissions to include all transitively contained child permissions.
     */
    public static Set<InstancePermission> expand(Set<InstancePermission> permissions) {
        Set<InstancePermission> expanded = EnumSet.copyOf(permissions);
        for (InstancePermission permission : permissions) {
            expanded.addAll(permission.allChildren());
        }
        return expanded;
    }

    /**
     * Returns all permissions transitively included by this permission (direct and indirect children).
     */
    public Set<InstancePermission> allChildren() {
        if (allChildren == null) {
            allChildren = EnumSet.noneOf(InstancePermission.class);
            for (InstancePermission child : children) {
                allChildren.add(child);
                allChildren.addAll(child.allChildren());
            }
        }
        return allChildren;
    }

    /**
     * Checks whether this permission transitively includes the given permission.
     */
    public boolean includes(InstancePermission permission) {
        return allChildren().contains(permission);
    }
}
