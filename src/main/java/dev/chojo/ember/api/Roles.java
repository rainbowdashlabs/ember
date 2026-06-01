/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import io.javalin.security.RouteRole;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.chojo.ember.api.RoleScope.STATION_PERMISSION;
import static dev.chojo.ember.api.RoleScope.STATION_USER;
import static dev.chojo.ember.api.RoleScope.SYSTEM_USER;

/**
 * Hierarchical role system used for route-level access control.
 * Each role can include child roles, forming a tree where higher roles transitively grant
 * all permissions of their children (e.g. {@link #MANAGER} includes all management roles).
 */
public enum Roles implements RouteRole {
    /**
     * Allows logging in
     */
    LOGIN(STATION_PERMISSION),
    /**
     * A general user on a station
     */
    USER(STATION_USER),
    /**
     * General access to edit own data
     */
    MEMBER(STATION_USER, USER),
    /**
     * Trial member with limited access
     */
    TRIAL(STATION_USER, USER, LOGIN),
    /**
     * Manage data of other members that are assigned to this member
     */
    GUARDIAN(STATION_USER, USER, LOGIN),
    /**
     * Team member with elevated access (e.g. team leads, instructors)
     */
    TEAM(STATION_USER, LOGIN, USER),
    /**
     * Manage attendence
     */
    ATTENDANCE_MANAGER(STATION_PERMISSION),
    /**
     * Export attendance reports
     */
    ATTENDANCE_EXPORT_MANAGER(STATION_PERMISSION),
    /**
     * Manage inventory
     */
    INVENTORY_MANAGER(STATION_PERMISSION),
    /**
     * Manage events and breaks
     */
    EVENT_MANAGER(STATION_PERMISSION),

    /**
     * Manage member data
     */
    MEMBER_MANAGER(STATION_PERMISSION),
    /**
     * Create and manage news entries
     */
    NEWS_MANAGER(STATION_PERMISSION),
    /**
     * Create and manage polls/forms
     */
    POLL_MANAGER(STATION_PERMISSION),
    /**
     * Manage lost and found items
     */
    LOST_AND_FOUND_MANAGER(STATION_PERMISSION),
    /**
     * Manage waiting lists
     */
    WAITLIST_MANAGER(STATION_PERMISSION),
    /**
     * Manage quiz catalogs, tests and grading
     */
    QUIZ_MANAGER(STATION_PERMISSION),
    /**
     * Manage knowledge base content
     */
    KNOWLEDGE_MANAGER(STATION_PERMISSION),
    /**
     * Manage federation partnerships and sharing
     */
    FEDERATION_MANAGER(STATION_PERMISSION),
    /**
     * Create and manage boards
     */
    BOARD_MANAGER(STATION_PERMISSION),
    /**
     * Create and manage test protocols (Prüfungsbögen)
     */
    PROTOCOL_MANAGER(STATION_PERMISSION),
    /**
     * Grade members in test protocol runs
     */
    PROTOCOL_TESTER(STATION_PERMISSION),
    /**
     * Manage everything. Includes all other management roles
     */
    MANAGER(
            STATION_PERMISSION,
            MEMBER,
            TEAM,
            ATTENDANCE_MANAGER,
            ATTENDANCE_EXPORT_MANAGER,
            INVENTORY_MANAGER,
            EVENT_MANAGER,
            MEMBER_MANAGER,
            NEWS_MANAGER,
            POLL_MANAGER,
            LOST_AND_FOUND_MANAGER,
            WAITLIST_MANAGER,
            QUIZ_MANAGER,
            KNOWLEDGE_MANAGER,
            BOARD_MANAGER,
            PROTOCOL_MANAGER,
            PROTOCOL_TESTER,
            FEDERATION_MANAGER),
    /**
     * Manange the software itself.
     */
    ADMIN(SYSTEM_USER);

    /**
     * Roles that must not be removed from a member to prevent lockouts.
     */
    public static final Set<Roles> PROTECTED_ROLES = Set.of(MEMBER_MANAGER, MANAGER);

    /**
     * Role pairs that are mutually exclusive and cannot be assigned to the same member.
     */
    public static final Map<Roles, Set<Roles>> CONFLICTING_ROLES = Map.of(
            MEMBER,
            Set.of(
                    GUARDIAN,
                    TEAM,
                    MANAGER,
                    ADMIN,
                    ATTENDANCE_MANAGER,
                    ATTENDANCE_EXPORT_MANAGER,
                    INVENTORY_MANAGER,
                    EVENT_MANAGER,
                    MEMBER_MANAGER,
                    NEWS_MANAGER,
                    POLL_MANAGER,
                    LOST_AND_FOUND_MANAGER,
                    WAITLIST_MANAGER,
                    QUIZ_MANAGER,
                    KNOWLEDGE_MANAGER,
                    BOARD_MANAGER,
                    PROTOCOL_MANAGER,
                    PROTOCOL_TESTER,
                    FEDERATION_MANAGER),
            GUARDIAN,
            Set.of(MEMBER));

    private final Roles[] children;
    private Set<Roles> allChildren;
    private final RoleScope scope;

    Roles(RoleScope scope) {
        this.scope = scope;
        this.children = new Roles[0];
    }

    Roles(RoleScope scope, Roles... children) {
        this.scope = scope;
        this.children = children;
    }

    /**
     * Maps a database role name to the corresponding enum value.
     */
    public static Roles fromDbName(String dbName) {
        try {
            return valueOf(dbName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Expands a set of roles to include all transitively contained child roles.
     */
    public static Set<Roles> expand(Set<Roles> roles) {
        Set<Roles> expanded = EnumSet.copyOf(roles);
        for (Roles role : roles) {
            expanded.addAll(role.allChildren());
        }
        return expanded;
    }

    /**
     * Returns the direct child roles of this role.
     *
     * @return array of direct child roles
     */
    public Roles[] getChildren() {
        return children;
    }

    /**
     * Returns all roles transitively included by this role (direct + indirect children).
     */
    public Set<Roles> allChildren() {
        if (allChildren == null) {
            allChildren = EnumSet.noneOf(Roles.class);
            for (Roles child : children) {
                allChildren.add(child);
                allChildren.addAll(child.allChildren());
            }
        }
        return allChildren;
    }

    /**
     * Checks whether this role transitively includes the given role.
     */
    public boolean includes(Roles role) {
        return allChildren().contains(role);
    }

    /**
     * Returns the scope of this role.
     */
    public RoleScope scope() {
        return scope;
    }

    /**
     * Returns all roles with {@link RoleScope#STATION_USER} scope.
     */
    public static Set<Roles> userRoles() {
        return Arrays.stream(values())
                .filter(r -> r.scope() == RoleScope.STATION_USER)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Roles.class)));
    }

    /**
     * Returns all roles with {@link RoleScope#STATION_PERMISSION} scope.
     */
    public static Set<Roles> permissionRoles() {
        return Arrays.stream(values())
                .filter(r -> r.scope() == RoleScope.STATION_PERMISSION)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Roles.class)));
    }
}
