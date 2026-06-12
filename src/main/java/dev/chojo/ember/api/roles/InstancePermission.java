/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.roles;

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
     * Expands a set of roles to include all transitively contained child roles.
     */
    public static Set<InstancePermission> expand(Set<InstancePermission> roles) {
        Set<InstancePermission> expanded = EnumSet.copyOf(roles);
        for (InstancePermission role : roles) {
            expanded.addAll(role.allChildren());
        }
        return expanded;
    }

    /**
     * Returns all roles transitively included by this role (direct and indirect children).
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
     * Checks whether this role transitively includes the given role.
     */
    public boolean includes(InstancePermission role) {
        return allChildren().contains(role);
    }
}
